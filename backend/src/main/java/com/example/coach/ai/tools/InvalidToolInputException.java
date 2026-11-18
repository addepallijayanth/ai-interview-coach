package com.example.coach.ai.tools;

/** Bad tool input. The message is sent back to the model so it can fix the call. */
public class InvalidToolInputException extends RuntimeException {

    public InvalidToolInputException(String message) {
        super(message);
    }
}
