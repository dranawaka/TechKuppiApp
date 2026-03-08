package com.tech.techkuppiapp.dto;

/**
 * Request body for batch question generation. Optional; defaults used if not provided.
 */
public class BatchQuestionsRequest {

    /** Number of questions to generate (default 5, max typically 10–20 for one API call). */
    private Integer count = 5;

    /** Optional topic: java, aws, kafka, database, spring, springboot, genai. If null/blank, generic questions are generated. */
    private String topic;

    public BatchQuestionsRequest() {
    }

    public BatchQuestionsRequest(Integer count, String topic) {
        this.count = count != null && count > 0 ? Math.min(count, 20) : 5;
        this.topic = topic;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count == null || count <= 0 ? 5 : Math.min(count, 20);
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }
}
