package com.rapidswing.generator;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A minimal template language for the project templates:
 * <ul>
 *   <li>{@code {{name}}} inserts a value; an unknown name is an error, so typos cannot slip into generated code</li>
 *   <li>{@code {{#if flag}}...{{/if}}} and {@code {{#unless flag}}...{{/unless}}} include text conditionally;
 *       blocks can be nested</li>
 * </ul>
 * A line holding only a block tag disappears completely, so conditional lines leave no blank lines behind.
 */
public final class Template {

    private static final Pattern STANDALONE_TAG = Pattern.compile("(?m)^[ \\t]*(\\{\\{[#/][^}]+\\}\\})[ \\t]*\\r?\\n");
    /** An innermost block: one that contains no other block opening. */
    private static final Pattern BLOCK = Pattern.compile(
            "\\{\\{#(if|unless) (\\w+)\\}\\}((?:(?!\\{\\{#(?:if|unless) ).)*?)\\{\\{/\\1\\}\\}", Pattern.DOTALL);
    private static final Pattern VARIABLE = Pattern.compile("\\{\\{(\\w+)\\}\\}");

    private Template() {
    }

    public static String render(String template, Map<String, String> values, Map<String, Boolean> flags) {
        String text = STANDALONE_TAG.matcher(template).replaceAll("$1");
        while (true) {
            Matcher block = BLOCK.matcher(text);
            if (!block.find()) {
                break;
            }
            StringBuffer out = new StringBuffer();
            do {
                Boolean flag = flags.get(block.group(2));
                if (flag == null) {
                    throw new IllegalArgumentException("Unknown template flag: " + block.group(2));
                }
                boolean include = "if".equals(block.group(1)) == flag;
                block.appendReplacement(out, Matcher.quoteReplacement(include ? block.group(3) : ""));
            } while (block.find());
            block.appendTail(out);
            text = out.toString();
        }
        if (text.contains("{{#") || text.contains("{{/")) {
            throw new IllegalArgumentException("Unbalanced block tags in template");
        }
        Matcher variable = VARIABLE.matcher(text);
        StringBuffer out = new StringBuffer();
        while (variable.find()) {
            String value = values.get(variable.group(1));
            if (value == null) {
                throw new IllegalArgumentException("Unknown template variable: " + variable.group(1));
            }
            variable.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        variable.appendTail(out);
        return out.toString();
    }
}
