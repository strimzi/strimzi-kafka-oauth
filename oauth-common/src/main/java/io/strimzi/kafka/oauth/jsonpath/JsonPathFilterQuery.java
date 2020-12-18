/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.jsonpath;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * This class implements parsing JsonPath filter query syntax inspired by:
 *
 *    https://github.com/json-path/JsonPath
 *
 * Given the following content of the JWT token:
 * <pre>
 *   {
 *     "aud": ["uma_authorization", "kafka"],
 *     "iss": "https://auth-server/token/",
 *     "iat": 0,
 *     "exp": 600,
 *     "sub": "username",
 *     "custom": "custom-value",
 *     "roles": {
 *       "client-roles": {
 *         "kafka": ["kafka-user"]
 *       }
 *     },
 *     "custom-level": 9
 *   }
 * </pre>
 *
 * Some examples of valid queries are:
 *
 * <pre>
 *   "@.exp &lt; 1000"
 *   "@.custom == 'custom-value'"
 *   "@.custom == 'custom-value' and @.exp &gt; 1000"
 *   "@.custom == 'custom-value' or @.exp &gt;= 1000"
 *   "@.custom == 'custom-value' &amp;&amp; @.exp &lt;= 1000"
 *   "@.custom != 'custom-value'"
 *   "@.iat != null"
 *   "@.iat == null"
 *   "@.custom in ['some-custom-value', 42, 'custom-value']"
 *   "@.custom nin ['some-custom-value', 42, 'custom-value']"
 *   "@.custom-level in [1,8,9,20]"
 *   "@.custom-level nin [1,2,3]"
 *   "@.roles.client-roles.kafka != null"
 *   "'kafka' in @.aud"
 *   '"kafka-user" in @.roles.client-roles.kafka'
 *   "@.exp &gt; 1000 || 'kafka' in @.aud"
 *   "(@.custom == 'custom-value' or @.custom == 'custom-value2')"
 *   "@.roles.client-roles.kafka != null or (@.exp &gt; 1000 &amp;&amp; @.custom == 'custom-value')"
 *   "('kafka' in @.aud || @.custom == 'custom-value') and @.exp &gt; 1000"
 *   "(('kafka' in @.aud || @.custom == 'custom-value') and @.exp &gt; 1000)"
 *   "((('kafka' in @.aud || @.custom == 'custom-value') and @.exp &gt; 1000))"
 *   "@.exp =~ /^6[0-9][0-9]$/"
 *   "@.custom =~ /^custom-.+$/"
 *   "@.custom =~ /(?i)^CUSTOM-.+$/"
 *   "@.iss =~ /https:\/\/auth-server\/.+/"
 *   "!(@.missing noneof [null, 'username'])"
 * </pre>
 *
 * This class only implements a subset of the JsonPath syntax. It is focused on filter matching - answering the question if the JWT token matches the selector or not.
 * <p>
 * Main difference with the JsonPath is that the attribute paths using '@' match relative to root JSON object rather than any child attribute.
 * For equivalent queries using other JsonPath implementations one would have to wrap the JWT object into another attribute, for example:
 * <pre>
 *   {
 *       "token": {
 *         "sub": "username",
 *         "custom": "custom value",
 *         ...
 *       }
 *   }
 * </pre>
 * and perform queries of the format:
 * <pre>
 *    $[*][?(QUERY)]
 * </pre>
 * For example: '$[*][?(@.custom == 'custom value')]'
 *
 * Some other differences are:
 * <ul>
 * <li> the use of 'or' / 'and' in addition to '||' / '&amp;&amp;'</li>
 * <li> the requirement to use whitespace between operands and operators</li>
 * <li> the RegEx operator using the {@link java.util.regex.Pattern} regex format, where you specify options as part of the query,
 * for example starting the regex with: (?i) to turn on case-insensitive matching</li>
 * </ul>
 *
 * Usage:
 * <pre>
 *   JsonPathFilterQuery query = new JsonPathFilterQuery("@.custom == 'value'");
 *   boolean match = query.matches(jsonObject);
 * </pre>
 *
 * Query is parsed in the first line and any errors during parsing result
 * in {@link JsonPathFilterQueryException}.
 *
 * Matching is thread safe. The normal usage pattern is to initialise the JsonPathFilterQuery object once,
 * and query it many times concurrently against json objects.
 */
public class JsonPathFilterQuery {

    private static final Logger log = LoggerFactory.getLogger(JsonPathFilterQuery.class);

    private final ComposedPredicateNode parsed;

    private JsonPathFilterQuery(String query) {
        this.parsed = readComposedPredicate(new ParsingContext(query.toCharArray()));
    }

    /**
     * Construct a new JsonPathFilterQuery
     *
     * @param query The query using the JsonPath filter syntax
     * @return New JsonPathFilerQuery instance
     */
    public static JsonPathFilterQuery parse(String query) {
        return new JsonPathFilterQuery(query);
    }

    /**
     * Match the json objects against the filter query.
     *
     * @param jsonObject Jackson DataBind object
     * @return true if the object matches the filter, false otherwise
     */
    public boolean matches(JsonNode jsonObject) {
        return new Matcher(parsed).matches(jsonObject);
    }

    private ComposedPredicateNode readComposedPredicate(ParsingContext ctx) {
        List<ExpressionNode> expressions = new ArrayList<>();

        Logical operator = null;
        do {
            ctx.resetStart();
            AbstractPredicateNode predicate;

            boolean negated = ctx.readExpected(Constants.NOT);
            if (negated) {
                ctx.skipWhiteSpace();
            }
            boolean bracket = readDelim(ctx, Constants.LEFT_BRACKET);
            if (bracket) {
                predicate = readComposedPredicate(ctx);
                if (!readDelim(ctx, Constants.RIGHT_BRACKET)) {
                    throw new JsonPathFilterQueryException("Failed to parse query - expected ')'" + ctx.toString());
                }
            } else if (negated) {
                throw new JsonPathFilterQueryException("Failed to parse query - expected '('" + ctx.toString());
            } else {
                predicate = readPredicate(ctx);
            }
            if (predicate == null) {
                throw new JsonPathFilterQueryException("Failed to parse query: " + ctx.toString());
            }
            validate(ctx, predicate);
            expressions.add(expression(operator, negated, predicate));
        } while ((operator = readOrOrAnd(ctx)) != null);

        return new ComposedPredicateNode(expressions);
    }

    private boolean readDelim(ParsingContext ctx, char delim) {
        ctx.skipWhiteSpace();
        if (ctx.eol()) {
            return false;
        }
        return ctx.readExpected(delim);
    }

    private void validate(ParsingContext ctx, AbstractPredicateNode node) {
        if (node instanceof PredicateNode) {
            PredicateNode predicate = (PredicateNode) node;

            OperatorNode op = predicate.getOp();
            if (OperatorNode.EQ.equals(op)
                    || OperatorNode.NEQ.equals(op)
                    || OperatorNode.LT.equals(op)
                    || OperatorNode.GT.equals(op)
                    || OperatorNode.LTE.equals(op)
                    || OperatorNode.GTE.equals(op)) {

                validateComparator(ctx, predicate, op);

            } else if (OperatorNode.IN.equals(op) || OperatorNode.NIN.equals(op)) {
                validateInNin(ctx, predicate);

            } else if (OperatorNode.ANYOF.equals(op) || OperatorNode.NONEOF.equals(op)) {
                validateAnyOfNoneOf(predicate);

            } else if (OperatorNode.MATCH_RE.equals(op)) {
                validateMatchRegEx(ctx, predicate);
            }
        }
    }

    private void validateComparator(ParsingContext ctx, PredicateNode predicate, OperatorNode op) {
        if (!(predicate.getLval() instanceof PathNameNode)) {
            throw new JsonPathFilterQueryException("Value to the left of '" + op + "' has to be specified as an attribute path (for example: @.attr) - " + ctx.reset());
        }
        if (predicate.getRval() instanceof NullNode
                && !OperatorNode.EQ.equals(op)
                && !OperatorNode.NEQ.equals(op)) {
            throw new JsonPathFilterQueryException("Can not use 'null' to the right of '" + op + "' - " + ctx);
        }
    }

    private void validateMatchRegEx(ParsingContext ctx, PredicateNode predicate) {
        if (!(predicate.getLval() instanceof PathNameNode)) {
            throw new JsonPathFilterQueryException("Value to the left of =~ has to be specified as an attribute path (for example: @.attr) - " + ctx.reset());
        }
        if (!(predicate.getRval() instanceof RegexNode)) {
            throw new JsonPathFilterQueryException("Value to the right of =~ has to be specified as a regular expression (for example: /foo-.+/) - " + ctx);
        }
    }

    private void validateAnyOfNoneOf(PredicateNode predicate) {
        OperatorNode op = predicate.getOp();
        Node rNode = predicate.getRval();
        if (rNode == null || rNode instanceof NullNode) {
            throw new JsonPathFilterQueryException("Illegal state - can't have 'null' to the right of '" + op + "'  (try 'in [null]' or '== null')");
        }

        if (!(rNode instanceof ListNode)) {
            throw new JsonPathFilterQueryException("Value to the right of '" + op + "' has to be an array (for example: ['value1', 'value2']");
        }

        Node lNode = predicate.getLval();
        if (!(lNode instanceof PathNameNode)) {
            throw new JsonPathFilterQueryException("Value to the left of '" + op + "' has to be specified as an attribute path (for example: @.attr)");
        }
    }

    private void validateInNin(ParsingContext ctx, PredicateNode predicate) {
        OperatorNode op = predicate.getOp();
        Node rNode = predicate.getRval();
        if (NullNode.INSTANCE == rNode) {
            throw new JsonPathFilterQueryException("Can not use 'null' to the right of '" + op + "'. (Try '" + op + " [null]' or '"  + (OperatorNode.IN.equals(op) ? '=' : '!') + "= null') - " + ctx);
        }
        if (!PathNameNode.class.isAssignableFrom(rNode.getClass())
                && !ListNode.class.isAssignableFrom(rNode.getClass())) {
            throw new JsonPathFilterQueryException("Value to the right of '" + op + "' has to be specified as an attribute path (for example: @.attr) or an array (for example: ['val1', 'val2']) - " + ctx);
        }
        Node lNode = predicate.getLval();
        if (!PathNameNode.class.isAssignableFrom(lNode.getClass())
                && !StringNode.class.isAssignableFrom(lNode.getClass())
                && !NumberNode.class.isAssignableFrom(lNode.getClass())
                && !NullNode.class.isAssignableFrom(lNode.getClass())) {
            throw new JsonPathFilterQueryException("Value to the left of '" + op + "' has to be specified as an attribute path (for example: @.attr), a string, a number or null - " + ctx.reset());
        }
    }

    private PredicateNode readPredicate(ParsingContext ctx) {
        Node lval = readOperand(ctx);
        if (lval == null) {
            return null;
        }

        OperatorNode op = readOperator(ctx);
        if (op == null) {
            return null;
        }
        ctx.resetStart();

        Node rval;
        if (op == OperatorNode.MATCH_RE) {
            rval = readRegex(ctx);
        } else {
            rval = readOperand(ctx);
        }

        if (rval == null) {
            throw new JsonPathFilterQueryException("Value expected to the right of '" + op + "' - " + ctx);
        }
        ctx.resetStart();
        return new PredicateNode(lval, op, rval);
    }

    private Node readRegex(ParsingContext ctx) {
        ctx.skipWhiteSpace();
        if (ctx.eol()) {
            return null;
        }

        boolean success = ctx.readExpected('/');
        if (!success) {
            throw new JsonPathFilterQueryException("Expected start of REGEX expression - " + ctx.toString());
        }

        String regex = null;
        int start = ctx.current;
        int c = ctx.read();
        int last = '/';

        while (c != Constants.EOL) {
            if (c == '/' && last != '\\') {
                regex = new String(ctx.buffer, start, ctx.current - start - 1);
                break;
            }
            last = c;
            c = ctx.read();
        }
        if (regex == null) {
            throw new JsonPathFilterQueryException("Expected end of RegEx expression - " + ctx.toString());
        }
        if (regex.length() == 0) {
            throw new JsonPathFilterQueryException("RegEx expression is empty - " + ctx.toString());
        }
        return new RegexNode(regex);
    }

    private OperatorNode readOperator(ParsingContext ctx) {
        ctx.skipWhiteSpace();
        if (ctx.eol()) {
            return null;
        }
        OperatorNode result = null;

        if (ctx.readExpectedWithDelims(Constants.EQ, Constants.SPACE)) {
            result = OperatorNode.EQ;
        } else if (ctx.readExpectedWithDelims(Constants.NEQ, Constants.SPACE)) {
            result = OperatorNode.NEQ;
        } else if (ctx.readExpectedWithDelims(Constants.LT, Constants.SPACE)) {
            result = OperatorNode.LT;
        } else if (ctx.readExpectedWithDelims(Constants.GT, Constants.SPACE)) {
            result = OperatorNode.GT;
        } else if (ctx.readExpectedWithDelims(Constants.LTE, Constants.SPACE)) {
            result = OperatorNode.LTE;
        } else if (ctx.readExpectedWithDelims(Constants.GTE, Constants.SPACE)) {
            result = OperatorNode.GTE;
        } else if (ctx.readExpectedWithDelims(Constants.MATCH_RE, Constants.SPACE)) {
            result = OperatorNode.MATCH_RE;
        } else if (ctx.readExpectedWithDelims(Constants.IN, Constants.SPACE)) {
            result = OperatorNode.IN;
        } else if (ctx.readExpectedWithDelims(Constants.NIN, Constants.SPACE)) {
            result = OperatorNode.NIN;
        } else if (ctx.readExpectedWithDelims(Constants.ANYOF, Constants.SPACE)) {
            result = OperatorNode.ANYOF;
        } else if (ctx.readExpectedWithDelims(Constants.NONEOF, Constants.SPACE)) {
            result = OperatorNode.NONEOF;
        }

        return result;
    }

    private Node readOperand(ParsingContext ctx) {
        Node node = readAttribute(ctx);
        if (node != null) {
            return node;
        }
        node = readArray(ctx);
        if (node != null) {
            return node;
        }
        node = readString(ctx);
        if (node != null) {
            return node;
        }
        node = readNumber(ctx);
        if (node != null) {
            return node;
        }
        node = readNull(ctx);
        return node;
    }

    private PathNameNode readAttribute(ParsingContext ctx) {
        ctx.skipWhiteSpace();
        if (ctx.eol()) {
            return null;
        }
        if (!ctx.readExpected('@')) {
            ctx.reset();
            return null;
        }
        AttributePathName pathname = readPathName(ctx);
        if (pathname == null) {
            ctx.reset();
            return null;
        }
        return new PathNameNode(pathname);
    }

    private ListNode readArray(ParsingContext ctx) {
        ctx.skipWhiteSpace();
        if (ctx.eol()) {
            return null;
        }
        if (!ctx.readExpected(Constants.LEFT_SQUARE_BRACKET)) {
            return null;
        }

        // Once we found the start of array we have to read it without errors
        ArrayList<Node> list = new ArrayList<>();

        Node node;
        int c;
        do {
            node = readArrayElement(ctx);
            if (node != null) {
                list.add(node);
            }
            ctx.skipWhiteSpace();
            c = ctx.read();

            if (c != ',' && c != Constants.RIGHT_SQUARE_BRACKET) {
                throw new JsonPathFilterQueryException("Unexpected character in array - " + ctx.toString());
            }
        } while (node != null && c != Constants.RIGHT_SQUARE_BRACKET);

        return new ListNode(list);
    }

    private Node readArrayElement(ParsingContext ctx) {
        ctx.skipWhiteSpace();
        if (ctx.eol()) {
            return null;
        }
        if (ctx.peek() == Constants.RIGHT_SQUARE_BRACKET) {
            return null;
        }
        Node node = readString(ctx);
        if (node != null) {
            return node;
        }
        node = readNumberInList(ctx);
        if (node != null) {
            return node;
        }
        node = readNull(ctx);
        return node;
    }

    private AttributePathName readPathName(ParsingContext ctx) {
        ArrayList<AttributePathName.Segment> segments = new ArrayList<>();
        AttributePathName.Segment segment = readPathNameSegment(ctx);
        while (segment != null) {
            segments.add(segment);
            segment = readPathNameSegment(ctx);
        }

        return segments.size() == 0 ? null : new AttributePathName(segments);
    }

    private AttributePathName.Segment readPathNameSegment(ParsingContext ctx) {
        if (!ctx.readExpected(Constants.DOT)) {
            return null;
        }

        if (ctx.peekForAny(Constants.DOT)) {
            throw new JsonPathFilterQueryException("Attribute pathname matching using '..' not supported - " + ctx);
        }

        if (ctx.eol()) {
            return null;
        }

        int start = ctx.current;
        int c;
        do {
            c = ctx.read();
        } while (c != Constants.EOL && c != Constants.SPACE && c != Constants.DOT && c != Constants.RIGHT_BRACKET);

        if (c != Constants.EOL) {
            ctx.unread();
        }
        return new AttributePathName.Segment(new String(ctx.buffer, start, ctx.current - start));
    }

    private StringNode readString(ParsingContext ctx) {
        ctx.skipWhiteSpace();
        if (ctx.eol()) {
            return null;
        }
        int start = ctx.current;

        boolean singleQuoted = ctx.readExpected(Constants.SINGLE);
        boolean doubleQuoted = !singleQuoted && ctx.readExpected(Constants.DOUBLE);

        if (singleQuoted || doubleQuoted) {
            boolean foundEnd = ctx.readUntil(singleQuoted ? Constants.SINGLE : Constants.DOUBLE);
            if (foundEnd) {
                // consume delimiter
                ctx.read();
                return new StringNode(new String(ctx.buffer, start + 1, ctx.current - start - 2));
            }
            throw new JsonPathFilterQueryException("Failed to read string - missing end quote - " + ctx);
        }
        // not a string
        return null;
    }

    private NumberNode readNumber(ParsingContext ctx) {
        return readNumber(ctx, false);
    }

    private NumberNode readNumberInList(ParsingContext ctx) {
        return readNumber(ctx, true);
    }

    private NumberNode readNumber(ParsingContext ctx, boolean inList) {
        ctx.skipWhiteSpace();
        if (ctx.eol()) {
            return null;
        }
        int start = ctx.current;
        boolean decimal = false;

        int endOffset = 1;
        int c = ctx.read();
        if (c == '-') {
            c = ctx.read();
        }
        while (c != Constants.EOL && c != Constants.SPACE) {
            if (!isDigit(c)) {
                if (c == Constants.DOT && !decimal) {
                    decimal = true;
                } else if (c == Constants.RIGHT_BRACKET || ((c == Constants.COMMA || c == Constants.RIGHT_SQUARE_BRACKET) && inList)) {
                    endOffset = 0;
                    ctx.unread();
                    break;
                } else {
                    if (log.isTraceEnabled()) {
                        log.trace("Invalid character for number: '" + c + "' - " + ctx);
                    }
                    ctx.resetTo(start);
                    return null;
                }
            }
            c = ctx.read();
        }
        int separator = c == Constants.EOL ? 0 : endOffset;
        return new NumberNode(new BigDecimal(ctx.buffer, start, ctx.current - start - separator));
    }

    private boolean isDigit(int c) {
        return c >= '0' && c <= '9';
    }

    private NullNode readNull(ParsingContext ctx) {
        ctx.skipWhiteSpace();
        if (ctx.eol()) {
            return null;
        }
        int start = ctx.current;
        if (!ctx.readExpected(Constants.NULL)) {
            return null;
        }

        // next one should be eol or ' ', ',', ']', or ')'
        boolean expected = ctx.peekForAny(Constants.SPACE, Constants.COMMA, Constants.RIGHT_SQUARE_BRACKET, Constants.RIGHT_BRACKET);
        if (!expected && !ctx.eol()) {
            ctx.resetTo(start);
            return null;
        }
        return NullNode.INSTANCE;
    }

    private ExpressionNode expression(Logical operator, boolean negated, AbstractPredicateNode predicate) {
        return new ExpressionNode(operator, negated, predicate);
    }

    private Logical readOrOrAnd(ParsingContext ctx) {
        ctx.skipWhiteSpace();
        if (ctx.eol()) {
            return null;
        }
        if (ctx.readExpectedWithDelims(Constants.OR, Constants.SPACE)) {
            return Logical.OR;
        }
        if (ctx.readExpectedWithDelims(Constants.OR_SYMBOLIC, Constants.SPACE)) {
            return Logical.OR;
        }
        if (ctx.readExpectedWithDelims(Constants.AND, Constants.SPACE)) {
            return Logical.AND;
        }
        if (ctx.readExpectedWithDelims(Constants.AND_SYMBOLIC, Constants.SPACE)) {
            return Logical.AND;
        }
        return null;
    }

}
