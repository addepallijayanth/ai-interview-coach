package com.example.coach.ai.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AgentTranscriptTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String CONVERSATION = """
            [{"role":"user","content":"Hi! I'm ready to start the mock interview."},
             {"role":"assistant","content":[
               {"type":"thinking","thinking":"","signature":"RXF1aXZhbGVudCBzaWduYXR1cmU="},
               {"type":"text","text":"Let me check your profile."},
               {"type":"tool_use","id":"toolu_01","name":"score_answer","input":{"question_no":1,"score":7}}]},
             {"role":"user","content":[{"type":"tool_result","tool_use_id":"toolu_01","content":"Saved","is_error":false}]},
             {"role":"system","content":"Call end_interview now."}]""";

    @Test
    void roundTripsThinkingBlocksToolCallsAndSystemMessagesUnchanged() throws Exception {
        AgentTranscript transcript = AgentTranscript.fromJson(CONVERSATION);
        String json = transcript.toJson();

        assertThat(JSON.readTree(json)).isEqualTo(JSON.readTree(CONVERSATION));
        assertThat(json).contains("{\"question_no\":1,\"score\":7}"); // key order kept: replay stays byte-identical
        assertThat(AgentTranscript.fromJson(json).toJson()).isEqualTo(json);
        assertThat(transcript.size()).isEqualTo(4);
    }

    @Test
    void knowsWhoseTurnItIs() {
        AgentTranscript transcript = AgentTranscript.fromJson(CONVERSATION);
        assertThat(transcript.awaitingModel()).isTrue();
        assertThat(transcript.endsWithSystemMessage()).isTrue();
        assertThat(AgentTranscript.empty().awaitingModel()).isFalse();
        assertThat(AgentTranscript.fromJson(null).isEmpty()).isTrue();
    }
}
