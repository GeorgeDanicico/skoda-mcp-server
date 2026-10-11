package com.gd.skoda.mcp;

import org.springframework.http.ProblemDetail;

/**
 * A tool call failed in a way the model should be told about. Created by {@link McpToolErrors}.
 *
 * <p>Spring AI turns a {@code RuntimeException} thrown from an {@code @McpTool} method into a
 * {@code CallToolResult} with {@code isError=true} whose text is the exception's message, a line
 * break and the message of its root cause. This exception uses that shape on purpose:
 * <ul>
 *   <li>message: one headline, e.g. {@code get_vehicle_location failed: MySkoda rate limit exceeded.}</li>
 *   <li>root cause: the details - what happened, what to do, whether and when a retry helps, and the
 *       problem type.</li>
 * </ul>
 * Neither carries a stack trace: the original exception is logged by {@link McpToolErrors}. Neither
 * text contains the MyŠkoda API key.
 */
public final class McpToolException extends RuntimeException {

    private final transient ProblemDetail problem;

    McpToolException(String headline, String details, ProblemDetail problem) {
        super(headline, new Details(details), false, false);
        this.problem = problem;
    }

    /** The problem this error was built from (same as the REST API would return). */
    public ProblemDetail problem() {
        return problem;
    }

    /** The detail text, i.e. the second line of the tool error. */
    public String details() {
        return getCause().getMessage();
    }

    /** Carrier for the detail line; see the class comment. */
    private static final class Details extends RuntimeException {

        Details(String message) {
            super(message, null, false, false);
        }
    }
}
