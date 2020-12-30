/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.jsonpath;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.List;

/**
 * <em>Matcher</em> is used for matching the JSON object against the parsed JSONPath filter query.
 *
 * This class is thread-safe, and can be used by multiple threads at the same time.
 *
 * Initialise the <em>Matcher</em> with the result of the {@link JsonPathFilterQuery#parse(String)} method.
 * Store the reference, and use it concurrently by calling the {@link Matcher#matches(JsonNode)} method,
 * passing it the JSON object to match against the parsed filter.
 */
class Matcher {

    private static final Logger log = LoggerFactory.getLogger(Matcher.class);

    private final ComposedPredicateNode parsed;

    Matcher(ComposedPredicateNode parsed) {
        this.parsed = parsed;
    }


    /**
     * Match the JSON object against the JSONPath filter query as described in {@link JsonPathFilterQuery}.
     *
     * @param json Jackson JsonObject to match
     * @return true if the object matches the filter, false otherwise
     */
    public boolean matches(JsonNode json) {
        return matches(json, parsed.getExpressions());
    }

    public boolean matches(JsonNode json, List<ExpressionNode> expressions) {
        BooleanEvaluator eval = new BooleanEvaluator();
        for (ExpressionNode expression : expressions) {

            Logical logical = expression.getOp();
            // short circuit for AND
            if (logical == Logical.AND && !eval.current) {
                return false;
            }
            // short circuit for OR
            if (logical == Logical.OR && eval.current) {
                return true;
            }
            AbstractPredicateNode node = expression.getPredicate();
            if (node instanceof ComposedPredicateNode) {
                boolean matches = matches(json, ((ComposedPredicateNode) node).getExpressions());
                eval.update(logical, expression.isNegated() ? !matches : matches);
            } else {
                updateEvaluationWithPredicateNode(eval, logical, json, node);
            }
        }
        return eval.current;
    }

    private void updateEvaluationWithPredicateNode(BooleanEvaluator eval, Logical logical, JsonNode json, AbstractPredicateNode node) {
        PredicateNode predicate = (PredicateNode) node;
        OperatorNode op = predicate.getOp();
        try {
            if (op == OperatorNode.EQ) {
                eval.update(logical, compareEquals(json, predicate));
            } else if (op == OperatorNode.NEQ) {
                eval.update(logical, !compareEquals(json, predicate));
            } else if (op == OperatorNode.GT) {
                eval.update(logical, compareGreaterThan(json, predicate));
            } else if (op == OperatorNode.LTE) {
                eval.update(logical, !compareGreaterThan(json, predicate));
            } else if (op == OperatorNode.LT) {
                eval.update(logical, compareLessThan(json, predicate));
            } else if (op == OperatorNode.GTE) {
                eval.update(logical, !compareLessThan(json, predicate));
            } else if (op == OperatorNode.IN) {
                eval.update(logical, containedIn(json, predicate));
            } else if (op == OperatorNode.NIN) {
                eval.update(logical, !containedIn(json, predicate));
            } else if (op == OperatorNode.MATCH_RE) {
                eval.update(logical, matchRegex(json, predicate));
            } else if (op == OperatorNode.ANYOF) {
                eval.update(logical, anyOf(json, predicate));
            } else if (op == OperatorNode.NONEOF) {
                eval.update(logical, noneOf(json, predicate));
            }
        } catch (IllegalStateException e) {
            log.error("Failed to evaluate expression due to internal error: " + node, e);
            eval.update(logical, false);

        } catch (Exception e) {
            if (log.isTraceEnabled()) {
                log.trace("Failed to evaluate expression: " + node, e);
            }
            eval.update(logical, false);
        }
    }

    private boolean matchRegex(JsonNode json, PredicateNode predicate) {
        Node lval = predicate.getLval();
        Node rval = predicate.getRval();

        // Additional validation is performed in JsonPathFilterQuery.validate()

        JsonKeyValue lNode = getAttributeJsonNode(json, (PathNameNode) lval);
        if (lNode == null || lNode.value == null) {
            return false;
        }

        if (!(lNode.value.isTextual() || lNode.value.isNumber())) {
            // can't match the type
            return false;
        }

        return ((RegexNode) rval).getPattern().matcher(lNode.value.asText()).matches();
    }

    private boolean compareEquals(JsonNode json, PredicateNode predicate) {
        Node lval = predicate.getLval();
        Node rval = predicate.getRval();

        if (lval instanceof PathNameNode) {
            JsonKeyValue lNode = getAttributeJsonNode(json, (PathNameNode) lval);

            if (rval instanceof PathNameNode) {
                JsonKeyValue rNode = getAttributeJsonNode(json, (PathNameNode) rval);
                if (lNode == null) {
                    return false;
                }
                if (lNode.value == null) {
                    return rNode != null && rNode.value == null;
                }

                return rNode != null && lNode.value.equals(rNode.value);
            }
            if (rval instanceof StringNode) {
                if (lNode == null || lNode.value == null) {
                    return false;
                }
                return lNode.value.isTextual() && lNode.value.asText().equals(((StringNode) rval).value);
            }
            if (rval instanceof NumberNode) {
                if (lNode == null) {
                    return false;
                }
                return lNode.value.isNumber() && ((NumberNode) rval).value.equals(new BigDecimal(lNode.value.asText()));
            }
            if (rval instanceof NullNode) {
                // We assume that the attribute not existing fulfills the == null condition
                return lNode == null || lNode.value == null;
            }
            return false;

        } else {
            // This validation is performed in JsonPathFilterQuery.validate()
            throw new IllegalStateException("Value left of == has to be specified as an attribute path e.g.: @.attr");
        }
    }

    private JsonKeyValue getAttributeJsonNode(JsonNode json, PathNameNode value) {
        String currentName = null;
        JsonNode current = json;

        for (AttributePathName.Segment segment : value.getPathname().getSegments()) {
            if (current == null) {
                return null;
            }

            currentName = segment.name();
            current = current.get(currentName);
        }
        return new JsonKeyValue(currentName, current);
    }

    private boolean compareLessThan(JsonNode json, PredicateNode predicate) {
        return compare(json, predicate) < 0;
    }

    private boolean compareGreaterThan(JsonNode json, PredicateNode predicate) {
        return compare(json, predicate) > 0;
    }

    private int compare(JsonNode json, PredicateNode predicate) {
        Node lval = predicate.getLval();
        Node rval = predicate.getRval();

        if (lval instanceof PathNameNode) {
            JsonKeyValue lNode = getAttributeJsonNode(json, (PathNameNode) lval);

            if (rval instanceof PathNameNode) {
                JsonKeyValue rNode = getAttributeJsonNode(json, (PathNameNode) rval);
                String rNodeValue = rNode == null ? null : rNode.value == null ? null : rNode.value.asText();

                if (lNode == null || lNode.value == null) {
                    throw new IllegalArgumentException("Unsupported comparison (null vs. " + rNodeValue + ")");
                }
                if (rNode == null || rNode.value == null) {
                    throw new IllegalArgumentException("Unsupported comparison (null vs. null)");
                }
                return compare(lNode.value, rNode.value);
            }
            if (rval instanceof StringNode) {
                if (lNode == null || !lNode.value.isTextual()) {
                    throw new IllegalArgumentException("Unsupported comparison (null vs. " + rval.toString() + ")");
                }
                return lNode.value.asText().compareTo(((StringNode) rval).value);
            }
            if (rval instanceof NumberNode) {
                if (lNode == null || lNode.value == null || !lNode.value.isNumber()) {
                    throw new IllegalArgumentException("Unsupported comparison (null vs. " + rval.toString() + ")");
                }

                double ldouble = lNode.value.asDouble();
                double rdouble = ((NumberNode) rval).value.doubleValue();

                return Double.compare(ldouble, rdouble);
            }

            throw new IllegalArgumentException("Unsupported comparison (" + lval + " .vs " + rval);
        }

        // This validation is performed in JsonPathFilterQuery.validate()
        throw new IllegalStateException("Value left of the operator has to be specified as an attribute path e.g.: @.attr");
    }

    private int compare(JsonNode val, JsonNode val2) {
        if (val == null && val2 == null) {
            return 0;
        }
        if (val != null && val2 != null) {
            if (val.isTextual()) {
                if (!val2.isTextual()) {
                    throw new IllegalArgumentException("Can't compare text value to non-text value (" + val + " vs. " + val2);
                }
                return val.asText().compareTo(val2.asText());
            }
            if (val.isNumber()) {
                if (!val2.isNumber()) {
                    throw new IllegalArgumentException("Can't compare a number value to a non-number value (" + val + " vs. " + val2);
                }
                return Double.compare(val.asDouble(), val2.asDouble());
            }
        }
        throw new IllegalArgumentException("Unsupported comparison (" + val + " vs. " + val2 + ")");
    }

    private boolean containedIn(JsonNode json, PredicateNode predicate) {
        Node lval = predicate.getLval();
        Node rval = predicate.getRval();

        // Additional validation is performed in JsonPathFilterQuery.validate()

        if (lval instanceof PathNameNode) {
            JsonKeyValue lNode = getAttributeJsonNode(json, (PathNameNode) lval);

            if (rval instanceof PathNameNode) {
                return containsPathNameNodeInPathNameNode(json, (PathNameNode) rval, lNode);

            } else if (rval instanceof ListNode) {
                return containsJsonValueInListNode(lNode, (ListNode) rval);
            } else {
                // This validation is performed in JsonPathFilterQuery.validate()
                throw new IllegalStateException("Can't use 'null' to the right of 'in' (try 'in [null]' or '== null')");
            }

        } else if (lval instanceof StringNode) {
            return containsStringNode(json, (StringNode) lval, rval);
        } else if (lval instanceof NumberNode) {
            return containsNumberNode(json, (NumberNode) lval, rval);
        } else if (lval instanceof NullNode) {
            return containsNullNode(json, rval);
        } else {
            // This validation is performed in JsonPathFilterQuery.validate()
            throw new IllegalStateException("Value to the left of 'in' has to be specified as an attribute path (for example: @.attr), a string, a number or null");
        }
    }

    private boolean anyOf(JsonNode json, PredicateNode predicate) {
        return anyOf(json, predicate, "anyof");
    }

    private boolean noneOf(JsonNode json, PredicateNode predicate) {
        return !anyOf(json, predicate, "noneof");
    }

    private boolean anyOf(JsonNode json, PredicateNode predicate, String opname) {
        Node lval = predicate.getLval();
        Node rval = predicate.getRval();

        // Additional validation is performed in JsonPathFilterQuery.validate()

        JsonKeyValue lNode = getAttributeJsonNode(json, (PathNameNode) lval);
        if (lNode == null || lNode.value == null) {
            return false;
        }

        if (!lNode.value.isArray()) {
            if (log.isTraceEnabled()) {
                log.trace("Unsupported value type for left of '" + opname + "' - must be array (" + lval + ")");
            }
            return false;
        }

        Iterator<JsonNode> it = lNode.value.elements();
        ListNode list = (ListNode) rval;

        while (it.hasNext()) {
            JsonNode item = it.next();
            if (item.isTextual()) {
                if (list.contains(new StringNode(item.asText()))) {
                    return true;
                }
            } else if (item.isNumber()) {
                if (list.contains(new NumberNode(item.decimalValue()))) {
                    return true;
                }
            } else if (item.isNull()) {
                if (list.contains(NullNode.INSTANCE)) {
                    return true;
                }
            } else {
                if (log.isTraceEnabled()) {
                    log.trace("Unsupported value type in attribute left of '" + opname + "' (" + item + ")");
                }
            }
        }
        return false;
    }

    private boolean containsStringNode(JsonNode json, StringNode lval, Node rval) {
        if (rval instanceof PathNameNode) {
            JsonKeyValue rNode = getAttributeJsonNode(json, (PathNameNode) rval);
            if (rNode == null || rNode.value == null) {
                return false;
            }

            // if rnode.value is array compare all array items to lNode.value
            if (rNode.value.isArray()) {
                return containsStringNodeInJsonArray(lval, rNode);
            }
            if (log.isTraceEnabled()) {
                log.trace("Unsupported comparison: " + lval + " in " + rNode.value);
            }
            return false;

        } else if (rval instanceof ListNode) {
            ListNode rvalNode = (ListNode) rval;
            return rvalNode.contains(lval);
        } else {
            // This validation is performed in JsonPathFilterQuery.validate()
            throw new IllegalStateException("Value to the right of 'in' has to be specified as an attribute path (for example: @.attr) or an array (for example: ['val1', 'val2'])");
        }
    }

    private boolean containsNumberNode(JsonNode json, NumberNode lval, Node rval) {
        if (rval instanceof PathNameNode) {
            JsonKeyValue rNode = getAttributeJsonNode(json, (PathNameNode) rval);
            if (rNode == null || rNode.value == null) {
                return false;
            }

            // if rnode.value is array compare all array items to lNode.value
            if (rNode.value.isArray()) {
                return containsNumberNodeInJsonArray(lval, rNode);
            }
            if (log.isTraceEnabled()) {
                log.trace("Unsupported comparison: " + lval + " in " + rNode.value);
            }
            return false;

        } else if (rval instanceof ListNode) {
            ListNode rvalNode = (ListNode) rval;
            return rvalNode.contains(lval);
        } else {
            // This validation is performed in JsonPathFilterQuery.validate()
            throw new IllegalStateException("Value to the right of 'in' has to be specified as an attribute path (for example: @.attr) or an array (for example: ['val1', 'val2'])");
        }
    }

    private boolean containsNullNode(JsonNode json, Node rval) {
        if (rval instanceof PathNameNode) {
            JsonKeyValue rNode = getAttributeJsonNode(json, (PathNameNode) rval);
            if (rNode == null || rNode.value == null) {
                return false;
            }

            // if rnode.value is array compare all array items to lNode.value
            if (rNode.value.isArray()) {
                return containsNullNodeInJsonArray(rNode);
            }
            if (log.isTraceEnabled()) {
                log.trace("Unsupported comparison: null in " + rNode.value);
            }
            return false;

        } else if (rval instanceof ListNode) {
            ListNode rvalNode = (ListNode) rval;
            return rvalNode.contains(NullNode.INSTANCE);
        } else {
            // This validation is performed in JsonPathFilterQuery.validate()
            throw new IllegalStateException("Value to the right of 'in' has to be specified as an attribute path (for example: @.attr) or an array (for example: ['val1', 'val2'])");
        }
    }

    private boolean containsPathNameNodeInPathNameNode(JsonNode json, PathNameNode rval, JsonKeyValue lNode) {
        JsonKeyValue rNode = getAttributeJsonNode(json, rval);
        if (rNode == null || lNode == null) {
            return false;
        }
        if (lNode.value == null && rNode.value == null) {
            return true;
        }

        // if rnode.value is array compare all array items to lNode.value
        if (rNode.value.isArray()) {
            return containsJsonValueInJsonArray(lNode, rNode);
        }
        if (log.isTraceEnabled()) {
            log.trace("Unsupported comparison: " + lNode.value + " in " + rNode.value);
        }
        return false;
    }

    private boolean containsNullNodeInJsonArray(JsonKeyValue rNode) {
        Iterator<JsonNode> it = rNode.value.elements();
        while (it.hasNext()) {
            JsonNode item = it.next();
            if (item.isNull()) {
                return true;
            }
        }
        return false;
    }

    private boolean containsNumberNodeInJsonArray(NumberNode number, JsonKeyValue rNode) {
        Iterator<JsonNode> it = rNode.value.elements();
        while (it.hasNext()) {
            JsonNode item = it.next();
            if (item.isNumber() && number.value.equals(item.decimalValue())) {
                return true;
            }
        }
        return false;
    }

    private boolean containsStringNodeInJsonArray(StringNode string, JsonKeyValue rNode) {
        Iterator<JsonNode> it = rNode.value.elements();
        while (it.hasNext()) {
            JsonNode item = it.next();
            if (item.isTextual() && string.value.equals(item.asText())) {
                return true;
            }
        }
        return false;
    }

    private boolean containsJsonValueInListNode(JsonKeyValue lNode, ListNode rvalNode) {
        if (lNode == null || lNode.value == null) {
            return rvalNode.contains(NullNode.INSTANCE);
        }
        Node value;
        try {
            value = convertFromJsonNode(lNode.value);
        } catch (Exception e) {
            if (log.isTraceEnabled()) {
                log.trace("Failed to convert attribute value to one supported on the left of 'in' : " + lNode.value, e);
            }
            return false;
        }
        return rvalNode.contains(value);
    }

    private boolean containsJsonValueInJsonArray(JsonKeyValue lNode, JsonKeyValue rNode) {
        Iterator<JsonNode> it = rNode.value.elements();
        while (it.hasNext()) {
            JsonNode item = it.next();
            if (item.equals(lNode.value)) {
                return true;
            }
        }
        return false;
    }

    private Node convertFromJsonNode(JsonNode value) {
        if (value.isTextual()) {
            return new StringNode(value.asText());
        } else if (value.isNumber()) {
            return new NumberNode(value.decimalValue());
        } else if (value.isNull()) {
            return NullNode.INSTANCE;
        }
        throw new IllegalArgumentException("Unsupported element type: " + value);
    }
}
