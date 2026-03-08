package com.tech.techkuppiapp.dto;

import java.util.List;

/**
 * Response for batch question generation and save.
 */
public class BatchQuestionsResponse {

    private boolean success;
    private String message;
    private int requestedCount;
    private int savedCount;
    private List<Long> savedIds;

    public BatchQuestionsResponse(boolean success, String message, int requestedCount, int savedCount, List<Long> savedIds) {
        this.success = success;
        this.message = message;
        this.requestedCount = requestedCount;
        this.savedCount = savedCount;
        this.savedIds = savedIds != null ? List.copyOf(savedIds) : List.of();
    }

    public static BatchQuestionsResponse ok(int requestedCount, int savedCount, List<Long> savedIds) {
        return new BatchQuestionsResponse(true, "Batch questions generated and saved.", requestedCount, savedCount, savedIds);
    }

    public static BatchQuestionsResponse error(String message) {
        return new BatchQuestionsResponse(false, message, 0, 0, List.of());
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public int getRequestedCount() {
        return requestedCount;
    }

    public int getSavedCount() {
        return savedCount;
    }

    public List<Long> getSavedIds() {
        return savedIds;
    }
}
