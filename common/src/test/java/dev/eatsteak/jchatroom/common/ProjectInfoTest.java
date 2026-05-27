package dev.eatsteak.jchatroom.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectInfoTest {
    @Test
    void exposesProjectName() {
        assertEquals("adv-java-chatroom", ProjectInfo.name());
    }
}
