/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.jsonpath;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;

/**
 * This class implements parsing JsonPath filter query syntax inspired by:
 *
 *    https://github.com/json-path/JsonPath
 *
 * Given the following content of the JWT token:
 * <pre>
 *   {
 *     "aud": ["uma_authorization", "kafka"],
 *     "iss": "https://keycloak/token/",
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
 *         ...
 *       }
 *   }
 * </pre>
 * and perform queries of the format:
 * <pre>
 *    $[*][?(QUERY)]
 * </pre>
 * For example: '$[*][?(@.sub != null &amp;&amp; )]'
 *
 * Some other differences are:
 * <ul>
 * <li> the use of 'or' / 'and' in addition to '||' / '&amp;&amp;'</li>
 * <li> the requirement to use whitespace between operands and operators</li>
 * </ul>
 *
 * Usage:
 * <pre>
 *   JsonPathFilterQuery query = new JsonPathFilterQuery("@.custom == 'value'");
 *   boolean match = query.match(jsonObject);
 * </pre>
 *
 * Query is parsed in the first line and any errors during parsing result
 * in {@link JsonPathFilterQueryException}.
 *
 * Matching is thread safe. The normal usage pattern is to initialise the JsonPathFilterQuery object once,
 * and query it many times concurrently against json objects.
 */
public class JsonPathFilterQuery {

    private final StatementNode parsed;

    private JsonPathFilterQuery(String query) {
        this.parsed = readStatement(new ParsingContext(query.toCharArray()));
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
    public boolean match(JsonNode jsonObject) {
        return new Matcher(parsed).match(jsonObject);
    }

    private StatementNode readStatement(ParsingContext ctx) {
        StatementNode node = new StatementNode();

        Logical operator = null;
        do {
            ctx.resetStart();
            PredicateNode predicate = readPredicate(ctx);
            if (predicate == null) {
                throw new JsonPathFilterQueryException("Failed to parse query: " + ctx.toString());
            }
            validate(predicate);
            node.add(expression(operator, predicate));
        } while ((operator = readOrOrAnd(ctx)) != null);

        return node;
    }

    private void validate(PredicateNode predicate) {
        OperatorNode op = predicate.getOp();
        if (OperatorNode.EQ.equals(op)
                || OperatorNode.LT.equals(op)
                || OperatorNode.GT.equals(op)
                || OperatorNode.LTE.equals(op)
                || OperatorNode.GTE.equals(op)) {

            if (!(predicate.getLval() instanceof PathNameNode)) {
                throw new JsonPathFilterQueryException("Value to the left of '" + op + "' has to be specified as an attribute path (for example: @.attr)");
            }
        }
        if (OperatorNode.IN.equals(op)) {
            Node rNode = predicate.getRval();
            if (NullNode.INSTANCE == rNode) {
                throw new JsonPathFilterQueryException("Can not use 'null' to the right of 'in'. (Try 'in [null]' or '== null')");
            }
            if (!PathNameNode.class.isAssignableFrom(rNode.getClass())
                    && !ListNode.class.isAssignableFrom(rNode.getClass())) {
                throw new JsonPathFilterQueryException("Value to the right of 'in' has to be specified as an attribute path (for example: @.attr) or an array (for example: ['val1', 'val2'])");
            }
            Node lNode = predicate.getLval();
            if (!PathNameNode.class.isAssignableFrom(lNode.getClass())
                    && !StringNode.class.isAssignableFrom(lNode.getClass())
                    && !NumberNode.class.isAssignableFrom(lNode.getClass())
                    && !NullNode.class.isAssignableFrom(lNode.getClass())) {
                throw new RuntimeException("Value to the left of 'in' has to be specified as an attribute path (for example: @.attr), a string, a number or null");
            }
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

        Node rval = readOperand(ctx);
        if (rval == null) {
            return null;
        }
        ctx.resetStart();
        return new PredicateNode(lval, op, rval);
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
        if (!ctx.readExpected('[')) {
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

            if (c != ',' && c != ']') {
                throw new JsonPathFilterQueryException("Unexpected character in array - " + ctx.toString());
            }
        } while (node != null && c != ']');

        return new ListNode(list);
    }

    private Node readArrayElement(ParsingContext ctx) {
        ctx.skipWhiteSpace();
        if (ctx.eol()) {
            return null;
        }
        if (ctx.peek() == ']') {
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
        boolean deep = false;
        if (ctx.readExpected(Constants.DOT)) {
            deep = true;
        }

        if (ctx.eol()) {
            return null;
        }

        int start = ctx.current;
        int c;
        do {
            c = ctx.read();
        } while (c != Constants.EOL && c != Constants.SPACE && c != Constants.DOT);

        if (c != Constants.EOL) {
            ctx.unread();
        }
        return new AttributePathName.Segment(new String(ctx.buffer, start, ctx.current - start), deep);
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
        }

        // TODO set error
        //throw new JsonPathParseException("Failed to read string - missing ending quote", query, ctx.current);
        ctx.reset(start);
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
        while (c != Constants.EOL && c != Constants.SPACE) {
            if (!isDigit(c)) {
                if (c == Constants.DOT && !decimal) {
                    decimal = true;
                } else if ((c == ',' || c == ']') && inList) {
                    endOffset = 0;
                    ctx.unread();
                    break;
                } else {
                    // TODO set error
                    //throw new JsonPathParseException("Invalid character for number: '" + c + "'", query, ctx.current);
                    ctx.reset(start);
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

        // next one should be eol or ' '
        boolean expected = ctx.readExpected(Constants.SPACE);
        if (!expected && !ctx.eol()) {
            ctx.reset(start);
            return null;
        }
        return NullNode.INSTANCE;
    }

    private ExpressionNode expression(Logical operator, PredicateNode predicate) {
        return new ExpressionNode(operator, predicate);
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
