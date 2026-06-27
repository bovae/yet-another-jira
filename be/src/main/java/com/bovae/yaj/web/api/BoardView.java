package com.bovae.yaj.web.api;

import java.util.List;

public record BoardView(List<BoardColumn> columns) {

    private static final String STATE_NEW = "new";
    private static final String STATE_READY_FOR_IMPLEMENTATION = "ready_for_implementation";
    private static final String STATE_IN_PROGRESS = "in_progress";
    private static final String STATE_READY_FOR_ACCEPTANCE = "ready_for_acceptance";
    private static final String STATE_DONE = "done";

    private static final String TYPE_BUG = "bug";
    private static final String TYPE_FEATURE = "feature";
    private static final String TYPE_FIX = "fix";

    public static BoardView sample() {
        return new BoardView(List.of(
                new BoardColumn(
                        STATE_NEW,
                        "New",
                        List.of(
                                new BoardCard("YAJ-1", "Draft the team invitation flow", TYPE_FEATURE, "Onboarding"),
                                new BoardCard("YAJ-2", "Board fails to load on empty response", TYPE_BUG, null))),
                new BoardColumn(
                        STATE_READY_FOR_IMPLEMENTATION,
                        "Ready for Implementation",
                        List.of(new BoardCard(
                                "YAJ-3", "Add pagination to the ticket list endpoint", TYPE_FEATURE, "Board"))),
                new BoardColumn(
                        STATE_IN_PROGRESS,
                        "In Progress",
                        List.of(new BoardCard("YAJ-4", "Correct correlation-id echo on error paths", TYPE_FIX, null))),
                new BoardColumn(
                        STATE_READY_FOR_ACCEPTANCE,
                        "Ready for Acceptance",
                        List.of(new BoardCard("YAJ-5", "Persist drag-and-drop column order", TYPE_FEATURE, "Board"))),
                new BoardColumn(
                        STATE_DONE,
                        "Done",
                        List.of(new BoardCard(
                                "YAJ-6", "Bootstrap the runnable application skeleton", TYPE_FEATURE, null)))));
    }
}
