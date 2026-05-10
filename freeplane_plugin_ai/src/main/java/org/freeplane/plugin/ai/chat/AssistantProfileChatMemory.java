package org.freeplane.plugin.ai.chat;

import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.memory.ChatMemoryService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import org.freeplane.plugin.ai.chat.history.AssistantProfileTranscriptEntry;
import org.freeplane.plugin.ai.chat.history.ChatTranscriptEntry;
import org.freeplane.plugin.ai.chat.history.ChatTranscriptRole;
import org.freeplane.plugin.ai.tools.MessageBuilder;
import org.freeplane.plugin.ai.tools.utilities.ToolCaller;

public class AssistantProfileChatMemory implements ChatMemory {

    private final Object id;
    private final Function<Object, Integer> maxTokensProvider;
    private final ChatTokenEstimator tokenEstimator;
    private final int protectedRecentTurnCount;
    private final double historicalToolTokenShare;
    private ProfileInstructionFactory profileInstructionFactory;
    private GeneralSystemMessage generalSystemMessage;
    private final List<ChatMessage> conversationMessages = new ArrayList<>();
    private final List<HistoricalToolCycle> hiddenHistoricalToolCycles = new ArrayList<>();
    private int activeStartIndex;
    private final List<Integer> turnEndIndexes = new ArrayList<>();
    private int currentTurnCount;

    private AssistantProfileChatMemory(Builder builder) {
        this.id = ensureNotNull(builder.id, "id");
        this.maxTokensProvider = ensureNotNull(builder.maxTokensProvider, "maxTokensProvider");
        this.tokenEstimator = new ChatTokenEstimator(builder.tokenEstimatorModelNameProvider);
        this.protectedRecentTurnCount = ensureGreaterThanZero(builder.protectedRecentTurnCount,
            "protectedRecentTurnCount");
        this.historicalToolTokenShare = validateHistoricalToolTokenShare(builder.historicalToolTokenShare);
        this.profileInstructionFactory = resolveProfileInstructionFactory(builder.profileInstructionFactory);
        ensureGreaterThanZero(this.maxTokensProvider.apply(this.id), "maxTokens");
    }

    @Override
    public Object id() {
        return id;
    }

    @Override
    public void add(ChatMessage message) {
        if (message == null) {
            return;
        }
        discardRedoBranchIfNeeded();
        if (message instanceof TranscriptHiddenSystemMessage) {
            if (!containsInstructionOfType(TranscriptHiddenSystemMessage.class)) {
                addConversationMessage(message);
                addConversationMessage(new InstructionAckMessage());
                rebuildTurnBoundaries();
            }
            return;
        }
        if (message instanceof RemovedForSpaceSystemMessage) {
            markContextWindowStart();
            return;
        }
        if (message instanceof AssistantProfileSwitchMessage) {
            addConversationMessage(message);
            addConversationMessage(new InstructionAckMessage());
            rebuildTurnBoundaries();
            return;
        }
        if (message instanceof InstructionAckMessage) {
            return;
        }
        if (message instanceof SystemMessage) {
            setGeneralSystemMessage(toGeneralSystemMessage((SystemMessage) message));
            rebuildTurnBoundaries();
            return;
        }
        addConversationMessage(message);
        rebuildTurnBoundaries();
    }

    @Override
    public List<ChatMessage> messages() {
        return buildMessages(activeConversationEndIndex());
    }

    @Override
    public void clear() {
        generalSystemMessage = null;
        conversationMessages.clear();
        hiddenHistoricalToolCycles.clear();
        activeStartIndex = 0;
        turnEndIndexes.clear();
        currentTurnCount = 0;
    }

    public boolean canUndo() {
        return currentTurnCount > firstActiveTurnIndex();
    }

    public int conversationMessageCount() {
        return conversationMessages.size();
    }

    public void truncateConversationMessagesTo(int size) {
        int targetSize = Math.max(0, Math.min(size, conversationMessages.size()));
        while (conversationMessages.size() > targetSize) {
            removeConversationMessage(conversationMessages.size() - 1);
        }
        hiddenHistoricalToolCycles.clear();
        activeStartIndex = Math.min(activeStartIndex, targetSize);
        rebuildTurnBoundaries();
    }

    public boolean canRedo() {
        return currentTurnCount < turnEndIndexes.size();
    }

    public String undo() {
        if (!canUndo()) {
            return "";
        }
        int turnIndex = currentTurnCount - 1;
        int from = turnIndex == 0 ? 0 : turnEndIndexes.get(turnIndex - 1);
        from = Math.max(from, activeStartIndex);
        int to = turnEndIndexes.get(turnIndex);
        currentTurnCount = turnIndex;
        rebalanceActiveWindowForCurrentTurnRange();
        return findUserMessageInRange(from, to);
    }

    public void redo() {
        if (!canRedo()) {
            return;
        }
        currentTurnCount++;
        rebalanceActiveWindowForCurrentTurnRange();
    }

    public void initializeUndoRedoFromMessages() {
        rebuildTurnBoundaries();
    }

    void expandWindowAfterTranscriptRestoreIfUnderutilized() {
        rebuildTurnBoundaries();
        int endIndex = activeConversationEndIndex();
        if (endIndex <= 0) {
            return;
        }
        int maxTokens = maxTokensProvider.apply(id);
        ensureGreaterThanZero(maxTokens, "maxTokens");
        int startIndex = Math.min(activeStartIndex, endIndex);
        long activeTokens = estimateTotalTokensForRange(startIndex, endIndex);
        if (activeTokens >= maxTokens) {
            return;
        }
        int selectedStart = startIndex;
        while (true) {
            int previousTurnStart = previousTurnStartFor(selectedStart);
            if (previousTurnStart < 0) {
                break;
            }
            long expandedTokens = estimateTotalTokensForRange(previousTurnStart, endIndex);
            if (expandedTokens > maxTokens) {
                break;
            }
            selectedStart = previousTurnStart;
            if (expandedTokens >= maxTokens) {
                break;
            }
        }
        hiddenHistoricalToolCycles.clear();
        activeStartIndex = selectedStart;
    }

    public boolean evictOldestTurn() {
        rebuildTurnBoundaries();
        if (!canAdvanceWindowByTurnWithMinimumRetention(1)) {
            return false;
        }
        return advanceWindowByOneTurn();
    }

    public List<ChatTranscriptEntry> transcriptEntriesForPersistence() {
        List<ChatTranscriptEntry> entries = new ArrayList<>();
        int endIndex = activeConversationEndIndex();
        VisibleContextSelection selection = currentVisibleContextSelection(endIndex);
        int startIndex = selection.firstVisibleHistoryIndex();
        for (int index = 0; index < endIndex; index++) {
            if (index == startIndex && startIndex > 0 && endIndex > startIndex) {
                entries.add(new ChatTranscriptEntry(ChatTranscriptRole.REMOVED_FOR_SPACE_SYSTEM,
                    RemovedForSpaceSystemMessage.DEFAULT_TEXT));
            }
            if (!selection.includes(index)) {
                continue;
            }
            ChatMessage message = conversationMessages.get(index);
            ChatTranscriptEntry entry = toTranscriptEntry(message);
            if (entry != null) {
                entries.add(entry);
            }
        }
        return entries;
    }

    public List<ChatMessage> activeConversationMessagesForRendering() {
        return buildRawMessages(activeConversationEndIndex());
    }

    public List<ChatMemoryRenderEntry> activeConversationRenderEntries() {
        return buildRenderEntries(false);
    }

    public List<ChatMemoryRenderEntry> panelConversationRenderEntries() {
        return buildRenderEntries(false);
    }

    private List<ChatMemoryRenderEntry> buildRenderEntries(boolean includeMessagesBeforeActiveWindow) {
        int endIndex = activeConversationEndIndex();
        if (endIndex == 0) {
            return Collections.emptyList();
        }
        List<ChatMemoryRenderEntry> entries = new ArrayList<>();
        if (generalSystemMessage != null) {
            entries.add(ChatMemoryRenderEntry.forMessage(generalSystemMessage));
        }
        VisibleContextSelection selection = currentVisibleContextSelection(endIndex);
        int startIndex = selection.firstVisibleHistoryIndex();
        for (int index = startIndex; index < endIndex; index++) {
            if (!selection.includes(index)) {
                continue;
            }
            if (index == startIndex && startIndex > 0 && endIndex > startIndex) {
                entries.add(ChatMemoryRenderEntry.forMessage(new RemovedForSpaceSystemMessage()));
            }
            ChatMessage message = conversationMessages.get(index);
            if (message instanceof ToolCallSummaryMessage) {
                ToolCallSummaryMessage summaryMessage = (ToolCallSummaryMessage) message;
                entries.add(ChatMemoryRenderEntry.forToolSummary(summaryMessage.text(), summaryMessage.toolCaller()));
                continue;
            }
            entries.add(ChatMemoryRenderEntry.forMessage(message));
        }
        return entries;
    }

    public void markContextWindowStart() {
        hiddenHistoricalToolCycles.clear();
        activeStartIndex = Math.max(activeStartIndex, conversationMessages.size());
    }

    void addToolCallSummary(String summaryText, ToolCaller toolCaller) {
        if (summaryText == null || summaryText.trim().isEmpty()) {
            return;
        }
        conversationMessages.add(new ToolCallSummaryMessage(summaryText, toolCaller));
        rebuildTurnBoundaries();
    }

    ChatUsageTotals estimateTokenUsageForActiveWindow() {
        int endIndex = activeConversationEndIndex();
        return estimateTokenUsageForSelection(currentVisibleContextSelection(endIndex));
    }

    ChatUsageTotals estimateTokenUsageForFullConversation() {
        return estimateTokenUsageForRange(0, conversationMessages.size());
    }

    public boolean onResponseTokenUsage(TokenUsage ignoredUsage) {
        return evictIfNeededAfterResponse();
    }

    void setProfileInstructionFactory(ProfileInstructionFactory profileInstructionFactory) {
        this.profileInstructionFactory = resolveProfileInstructionFactory(profileInstructionFactory);
    }

    private boolean evictIfNeededAfterResponse() {
        int maxTokens = maxTokensProvider.apply(id);
        ensureGreaterThanZero(maxTokens, "maxTokens");
        long estimatedTokens = estimateTotalTokensForActiveWindow();
        if (estimatedTokens < maxTokens) {
            return false;
        }
        int resetTargetTokens = maxTokens / 4;
        int minimumTurnBlocksToKeep = minimumTurnBlocksToKeep(maxTokens);
        int endIndex = activeConversationEndIndex();
        VisibleContextSelection selection = selectVisibleContext(endIndex, resetTargetTokens);
        boolean changed = replaceHiddenHistoricalToolCycles(selection.hiddenHistoricalToolCycles());
        while (selection.visibleTokenCount() > resetTargetTokens) {
            if (!canAdvanceWindowByTurnWithMinimumRetention(minimumTurnBlocksToKeep)) {
                break;
            }
            if (!advanceWindowByOneTurn()) {
                break;
            }
            changed = true;
            endIndex = activeConversationEndIndex();
            selection = selectVisibleContext(endIndex, resetTargetTokens);
            replaceHiddenHistoricalToolCycles(selection.hiddenHistoricalToolCycles());
        }
        return changed;
    }

    private List<ChatMessage> buildMessages(int conversationEndIndex) {
        List<ChatMessage> messages = new ArrayList<>();
        if (generalSystemMessage != null) {
            messages.add(generalSystemMessage);
        }
        int endIndex = Math.max(0, Math.min(conversationEndIndex, conversationMessages.size()));
        VisibleContextSelection selection = currentVisibleContextSelection(endIndex);
        int startIndex = selection.firstVisibleHistoryIndex();
        int latestProfileSwitchIndex = findLatestProfileSwitchIndex(endIndex);
        UserMessage latestProfileInstruction = buildProfileInstructionForIndex(latestProfileSwitchIndex);
        if (latestProfileInstruction != null && latestProfileSwitchIndex >= 0
            && latestProfileSwitchIndex < startIndex) {
            messages.add(latestProfileInstruction);
        }
        for (int index = startIndex; index < endIndex; index++) {
            if (!selection.includes(index)) {
                continue;
            }
            ChatMessage message = conversationMessages.get(index);
            if (message instanceof AssistantProfileSwitchMessage) {
                if (index == latestProfileSwitchIndex && latestProfileInstruction != null) {
                    messages.add(latestProfileInstruction);
                }
                continue;
            }
            if (message instanceof ToolCallSummaryMessage) {
                continue;
            }
            if (message instanceof TranscriptHiddenSystemMessage
                || message instanceof RemovedForSpaceSystemMessage) {
                messages.add(MessageBuilder.buildSystemInstructionUserMessage(
                    ((SystemMessage) message).text()));
                continue;
            }
            messages.add(message);
        }
        return messages;
    }

    private List<ChatMessage> buildRawMessages(int conversationEndIndex) {
        List<ChatMessage> messages = new ArrayList<>();
        if (generalSystemMessage != null) {
            messages.add(generalSystemMessage);
        }
        int endIndex = Math.max(0, Math.min(conversationEndIndex, conversationMessages.size()));
        VisibleContextSelection selection = currentVisibleContextSelection(endIndex);
        for (int index = selection.firstVisibleHistoryIndex(); index < endIndex; index++) {
            if (!selection.includes(index)) {
                continue;
            }
            messages.add(conversationMessages.get(index));
        }
        return messages;
    }

    private int activeConversationEndIndex() {
        if (canRedo()) {
            int firstActive = firstActiveTurnIndex();
            if (currentTurnCount <= firstActive) {
                return activeStartIndex;
            }
            return turnEndIndexes.get(currentTurnCount - 1);
        }
        return conversationMessages.size();
    }

    private int conversationEndIndexForCurrentTurnRange() {
        if (canRedo()) {
            if (currentTurnCount <= 0) {
                return 0;
            }
            return turnEndIndexes.get(currentTurnCount - 1);
        }
        return conversationMessages.size();
    }

    private boolean containsInstructionOfType(Class<? extends SystemMessage> messageClass) {
        for (ChatMessage message : conversationMessages) {
            if (messageClass.isInstance(message)) {
                return true;
            }
        }
        return false;
    }

    private GeneralSystemMessage toGeneralSystemMessage(SystemMessage message) {
        if (message instanceof GeneralSystemMessage) {
            return (GeneralSystemMessage) message;
        }
        return new GeneralSystemMessage(message.text());
    }

    private void rebuildTurnBoundaries() {
        turnEndIndexes.clear();
        for (int index = 0; index < conversationMessages.size(); index++) {
            ChatMessage message = conversationMessages.get(index);
            if (!(message instanceof AiMessage) || message instanceof InstructionAckMessage) {
                continue;
            }
            AiMessage aiMessage = (AiMessage) message;
            if (!aiMessage.hasToolExecutionRequests()) {
                turnEndIndexes.add(index + 1);
            }
        }
        currentTurnCount = turnEndIndexes.size();
        int endIndex = activeConversationEndIndex();
        if (activeStartIndex > endIndex) {
            activeStartIndex = endIndex;
        }
    }

    private void discardRedoBranchIfNeeded() {
        if (!canRedo()) {
            return;
        }
        hiddenHistoricalToolCycles.clear();
        int keepSize = currentTurnCount == 0 ? 0 : turnEndIndexes.get(currentTurnCount - 1);
        while (conversationMessages.size() > keepSize) {
            removeConversationMessage(conversationMessages.size() - 1);
        }
        while (turnEndIndexes.size() > currentTurnCount) {
            turnEndIndexes.remove(turnEndIndexes.size() - 1);
        }
        activeStartIndex = Math.min(activeStartIndex, keepSize);
    }

    private int firstActiveTurnIndex() {
        int startIndex = Math.min(activeStartIndex, conversationMessages.size());
        for (int index = 0; index < turnEndIndexes.size(); index++) {
            int turnEnd = turnEndIndexes.get(index);
            if (turnEnd > startIndex) {
                return index;
            }
        }
        return turnEndIndexes.size();
    }

    private String findUserMessageInRange(int from, int to) {
        int safeFrom = Math.max(0, from);
        int safeTo = Math.min(to, conversationMessages.size());
        for (int index = safeTo - 1; index >= safeFrom; index--) {
            ChatMessage message = conversationMessages.get(index);
            if (message instanceof UserMessage) {
                String text = ((UserMessage) message).singleText();
                if (text != null && !text.startsWith(MessageBuilder.CONTROL_INSTRUCTION_PREFIX)) {
                    return text;
                }
            }
        }
        return "";
    }

    private ChatTranscriptEntry toTranscriptEntry(ChatMessage message) {
        if (message == null) {
            return null;
        }
        if (message instanceof AssistantProfileSwitchMessage) {
            AssistantProfileSwitchMessage profileMessage = (AssistantProfileSwitchMessage) message;
            return new AssistantProfileTranscriptEntry(
                profileMessage.getProfileId(),
                profileMessage.getProfileName(),
                false);
        }
        if (message instanceof RemovedForSpaceSystemMessage) {
            return new ChatTranscriptEntry(ChatTranscriptRole.REMOVED_FOR_SPACE_SYSTEM,
                ((RemovedForSpaceSystemMessage) message).text());
        }
        if (message instanceof ToolCallSummaryMessage) {
            return null;
        }
        if (message instanceof UserMessage) {
            String text = ((UserMessage) message).singleText();
            if (text == null || text.trim().isEmpty() || text.startsWith(MessageBuilder.CONTROL_INSTRUCTION_PREFIX)) {
                return null;
            }
            return new ChatTranscriptEntry(ChatTranscriptRole.USER, text);
        }
        if (message instanceof AiMessage && !(message instanceof InstructionAckMessage)) {
            String text = ((AiMessage) message).text();
            if (text == null || text.trim().isEmpty()) {
                return null;
            }
            return new ChatTranscriptEntry(ChatTranscriptRole.ASSISTANT, text);
        }
        return null;
    }

    private void setGeneralSystemMessage(GeneralSystemMessage message) {
        generalSystemMessage = message;
    }

    private void addConversationMessage(ChatMessage message) {
        conversationMessages.add(message);
    }

    private ChatMessage removeConversationMessage(int index) {
        return conversationMessages.remove(index);
    }

    private boolean advanceWindowByOneTurn() {
        rebuildTurnBoundaries();
        int endIndex = activeConversationEndIndex();
        int startIndex = Math.min(activeStartIndex, endIndex);
        int nextTurnEnd = findNextTurnEndAfter(startIndex);
        if (nextTurnEnd <= startIndex) {
            return false;
        }
        hiddenHistoricalToolCycles.clear();
        activeStartIndex = nextTurnEnd;
        rebuildTurnBoundaries();
        return true;
    }

    private void rebalanceActiveWindowForCurrentTurnRange() {
        int maxTokens = maxTokensProvider.apply(id);
        ensureGreaterThanZero(maxTokens, "maxTokens");
        int endIndex = conversationEndIndexForCurrentTurnRange();
        if (endIndex <= 0 || currentTurnCount <= 0) {
            activeStartIndex = 0;
            return;
        }
        int selectedStart = turnStartIndex(currentTurnCount - 1);
        for (int turnIndex = currentTurnCount - 2; turnIndex >= 0; turnIndex--) {
            int candidateStart = turnStartIndex(turnIndex);
            if (estimateTotalTokensForRange(candidateStart, endIndex) <= maxTokens) {
                selectedStart = candidateStart;
                continue;
            }
            break;
        }
        hiddenHistoricalToolCycles.clear();
        activeStartIndex = selectedStart;
    }

    private int turnStartIndex(int turnIndex) {
        if (turnIndex <= 0) {
            return 0;
        }
        return turnEndIndexes.get(turnIndex - 1);
    }

    private int previousTurnStartFor(int startIndex) {
        int safeStart = Math.max(0, startIndex);
        int previousTurnIndex = -1;
        for (int index = 0; index < turnEndIndexes.size(); index++) {
            int turnEnd = turnEndIndexes.get(index);
            if (turnEnd <= safeStart) {
                previousTurnIndex = index;
                continue;
            }
            break;
        }
        if (previousTurnIndex < 0) {
            return -1;
        }
        return turnStartIndex(previousTurnIndex);
    }

    private boolean canAdvanceWindowByTurnWithMinimumRetention(int minimumTurnBlocksToKeep) {
        return activeTurnRanges().size() > minimumTurnBlocksToKeep;
    }

    private int findNextTurnEndAfter(int startIndex) {
        for (int index = 0; index < turnEndIndexes.size(); index++) {
            int turnEnd = turnEndIndexes.get(index);
            if (turnEnd > startIndex) {
                return turnEnd;
            }
        }
        return -1;
    }

    private int alignVisibleStartIndex(int startIndex, int endIndex) {
        return alignVisibleStartIndex(startIndex, endIndex, null);
    }

    private int alignVisibleStartIndex(int startIndex, int endIndex, boolean[] inclusionMask) {
        int alignedStart = Math.max(0, Math.min(startIndex, endIndex));
        while (alignedStart < endIndex) {
            if (inclusionMask != null && !inclusionMask[alignedStart]) {
                alignedStart++;
                continue;
            }
            ChatMessage message = conversationMessages.get(alignedStart);
            if (message instanceof ToolCallSummaryMessage) {
                if (!hasVisibleMessageAfter(alignedStart + 1, endIndex, inclusionMask)) {
                    break;
                }
                alignedStart++;
                continue;
            }
            break;
        }
        return alignedStart;
    }

    private boolean hasVisibleMessageAfter(int startIndex, int endIndex, boolean[] inclusionMask) {
        int safeStart = Math.max(0, startIndex);
        int safeEnd = Math.min(endIndex, conversationMessages.size());
        for (int index = safeStart; index < safeEnd; index++) {
            if (inclusionMask != null && !inclusionMask[index]) {
                continue;
            }
            return true;
        }
        return false;
    }

    private int findLatestProfileSwitchIndex(int endIndex) {
        for (int index = endIndex - 1; index >= 0; index--) {
            if (conversationMessages.get(index) instanceof AssistantProfileSwitchMessage) {
                return index;
            }
        }
        return -1;
    }

    private UserMessage buildProfileInstructionForIndex(int messageIndex) {
        if (messageIndex < 0 || messageIndex >= conversationMessages.size()) {
            return null;
        }
        ChatMessage message = conversationMessages.get(messageIndex);
        if (!(message instanceof AssistantProfileSwitchMessage)) {
            return null;
        }
        AssistantProfileInstructionMessage profileInstruction =
            profileInstructionFactory.buildFor((AssistantProfileSwitchMessage) message);
        if (profileInstruction == null) {
            return null;
        }
        return MessageBuilder.buildSystemInstructionUserMessage(profileInstruction.singleText());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static AssistantProfileChatMemory withMaxTokens(int maxTokens) {
        return builder().maxTokens(maxTokens).build();
    }

    public static class Builder {

        private Object id = ChatMemoryService.DEFAULT;
        private Function<Object, Integer> maxTokensProvider;
        private Supplier<String> tokenEstimatorModelNameProvider = () -> null;
        private ProfileInstructionFactory profileInstructionFactory;
        private int protectedRecentTurnCount = 1;
        private double historicalToolTokenShare = 0.5d;

        public Builder id(Object id) {
            this.id = id;
            return this;
        }

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokensProvider = ignored -> maxTokens;
            return this;
        }

        public Builder dynamicMaxTokens(Function<Object, Integer> maxTokensProvider) {
            this.maxTokensProvider = maxTokensProvider;
            return this;
        }

        public Builder tokenEstimatorModelNameProvider(Supplier<String> tokenEstimatorModelNameProvider) {
            this.tokenEstimatorModelNameProvider = tokenEstimatorModelNameProvider;
            return this;
        }

        public Builder profileInstructionFactory(ProfileInstructionFactory profileInstructionFactory) {
            this.profileInstructionFactory = profileInstructionFactory;
            return this;
        }

        public Builder protectedRecentTurnCount(int protectedRecentTurnCount) {
            this.protectedRecentTurnCount = protectedRecentTurnCount;
            return this;
        }

        public Builder historicalToolTokenShare(double historicalToolTokenShare) {
            this.historicalToolTokenShare = historicalToolTokenShare;
            return this;
        }

        public AssistantProfileChatMemory build() {
            return new AssistantProfileChatMemory(this);
        }
    }

    interface ProfileInstructionFactory {
        AssistantProfileInstructionMessage buildFor(AssistantProfileSwitchMessage profileSwitchMessage);
    }

    private ProfileInstructionFactory resolveProfileInstructionFactory(ProfileInstructionFactory profileInstructionFactory) {
        if (profileInstructionFactory != null) {
            return profileInstructionFactory;
        }
        return profileSwitchMessage -> {
            if (profileSwitchMessage == null) {
                return null;
            }
            return new AssistantProfileInstructionMessage(
                profileSwitchMessage.getProfileId(),
                profileSwitchMessage.getProfileName(),
                "");
        };
    }

    private ChatUsageTotals estimateTokenUsageForRange(int startIndex, int endIndex) {
        long inputTokens = 0L;
        long outputTokens = 0L;
        int safeStart = Math.max(0, startIndex);
        int safeEnd = Math.min(endIndex, conversationMessages.size());
        for (int index = safeStart; index < safeEnd; index++) {
            ChatMessage message = conversationMessages.get(index);
            if (!isRemovableMessage(message)) {
                continue;
            }
            int tokenCount = tokenEstimator.estimateTokenCountInMessage(message);
            if (message instanceof AiMessage) {
                outputTokens += tokenCount;
            } else {
                inputTokens += tokenCount;
            }
        }
        return ChatUsageTotals.estimated(inputTokens, outputTokens);
    }

    private ChatUsageTotals estimateTokenUsageForSelection(VisibleContextSelection selection) {
        if (selection == null) {
            return ChatUsageTotals.estimated(0L, 0L);
        }
        long inputTokens = 0L;
        long outputTokens = 0L;
        int endIndex = Math.min(selection.inclusionMask().length, conversationMessages.size());
        for (int index = selection.firstVisibleHistoryIndex(); index < endIndex; index++) {
            if (!selection.includes(index)) {
                continue;
            }
            ChatMessage message = conversationMessages.get(index);
            if (!isRemovableMessage(message)) {
                continue;
            }
            int tokenCount = tokenEstimator.estimateTokenCountInMessage(message);
            if (message instanceof AiMessage) {
                outputTokens += tokenCount;
            } else {
                inputTokens += tokenCount;
            }
        }
        return ChatUsageTotals.estimated(inputTokens, outputTokens);
    }

    private long estimateTotalTokensForActiveWindow() {
        ChatUsageTotals totals = estimateTokenUsageForActiveWindow();
        return totals.getInputTokenCount() + totals.getOutputTokenCount();
    }

    private long estimateTotalTokensForRange(int startIndex, int endIndex) {
        ChatUsageTotals totals = estimateTokenUsageForRange(startIndex, endIndex);
        return totals.getInputTokenCount() + totals.getOutputTokenCount();
    }

    private int minimumTurnBlocksToKeep(int maxTokens) {
        List<ActiveTurnRange> ranges = activeTurnRanges();
        if (ranges.size() <= 1) {
            return 1;
        }
        ActiveTurnRange secondLast = ranges.get(ranges.size() - 2);
        ActiveTurnRange last = ranges.get(ranges.size() - 1);
        long twoTurnTokenCount = estimateTotalTokensForRange(secondLast.startIndex, last.endIndex);
        return twoTurnTokenCount <= maxTokens ? 2 : 1;
    }

    private List<ActiveTurnRange> activeTurnRanges() {
        List<ActiveTurnRange> ranges = new ArrayList<>();
        int endIndex = activeConversationEndIndex();
        int startIndex = Math.min(activeStartIndex, endIndex);
        int previousEnd = 0;
        for (int index = 0; index < turnEndIndexes.size(); index++) {
            int turnEnd = turnEndIndexes.get(index);
            int turnStart = previousEnd;
            previousEnd = turnEnd;
            if (turnEnd <= startIndex) {
                continue;
            }
            int rangeStart = Math.max(turnStart, startIndex);
            int rangeEnd = Math.min(turnEnd, endIndex);
            if (rangeEnd > rangeStart) {
                ranges.add(new ActiveTurnRange(rangeStart, rangeEnd));
            }
        }
        return ranges;
    }

    private boolean isRemovableMessage(ChatMessage message) {
        if (message == null) {
            return false;
        }
        if (message instanceof AssistantProfileSwitchMessage
            || message instanceof InstructionAckMessage
            || message instanceof TranscriptHiddenSystemMessage
            || message instanceof RemovedForSpaceSystemMessage
            || message instanceof ToolCallSummaryMessage
            || message instanceof GeneralSystemMessage) {
            return false;
        }
        if (message instanceof SystemMessage) {
            return false;
        }
        return message instanceof UserMessage
            || message instanceof AiMessage
            || message instanceof ToolExecutionResultMessage;
    }

    private VisibleContextSelection currentVisibleContextSelection(int conversationEndIndex) {
        return visibleContextSelectionForHiddenCycles(conversationEndIndex, hiddenHistoricalToolCycles);
    }

    private VisibleContextSelection selectVisibleContext(int conversationEndIndex, int targetTokens) {
        int endIndex = Math.max(0, Math.min(conversationEndIndex, conversationMessages.size()));
        int visibleStartIndex = Math.min(activeStartIndex, endIndex);
        List<HistoricalToolCycle> hiddenCycles = new ArrayList<>();
        if (endIndex <= visibleStartIndex) {
            return new VisibleContextSelection(visibleStartIndex, visibleStartIndex, new boolean[endIndex],
                hiddenCycles, 0L);
        }
        int historicalEndIndex = firstProtectedTurnStartIndex(endIndex);
        long protectedTokens = estimateTotalTokensForRange(historicalEndIndex, endIndex);
        long historicalTokens = Math.max(0L, (long) targetTokens - protectedTokens);
        long historicalToolTokenCap = (long) Math.floor(historicalTokens * historicalToolTokenShare);
        List<HistoricalToolCycle> historicalCycles = collectHistoricalToolCycles(historicalEndIndex);
        hiddenCycles.addAll(trimHistoricalToolCycles(historicalCycles, historicalToolTokenCap));
        return visibleContextSelectionForHiddenCycles(endIndex, hiddenCycles);
    }

    private VisibleContextSelection visibleContextSelectionForHiddenCycles(int conversationEndIndex,
                                                                           List<HistoricalToolCycle> hiddenCycles) {
        int endIndex = Math.max(0, Math.min(conversationEndIndex, conversationMessages.size()));
        int visibleStartIndex = Math.min(activeStartIndex, endIndex);
        boolean[] inclusionMask = new boolean[endIndex];
        for (int index = visibleStartIndex; index < endIndex; index++) {
            inclusionMask[index] = true;
        }
        for (HistoricalToolCycle cycle : hiddenCycles) {
            int hiddenStart = Math.max(cycle.startIndex(), visibleStartIndex);
            int hiddenEnd = Math.min(cycle.endIndex(), endIndex);
            for (int index = hiddenStart; index < hiddenEnd; index++) {
                inclusionMask[index] = false;
            }
        }
        int firstVisibleHistoryIndex = alignVisibleStartIndex(visibleStartIndex, endIndex, inclusionMask);
        long visibleTokenCount = estimateVisibleTokens(inclusionMask, firstVisibleHistoryIndex, endIndex);
        return new VisibleContextSelection(visibleStartIndex, firstVisibleHistoryIndex, inclusionMask,
            new ArrayList<>(hiddenCycles), visibleTokenCount);
    }

    private long estimateVisibleTokens(boolean[] inclusionMask, int startIndex, int endIndex) {
        long total = 0L;
        int safeStart = Math.max(0, Math.min(startIndex, endIndex));
        int safeEnd = Math.min(endIndex, conversationMessages.size());
        for (int index = safeStart; index < safeEnd; index++) {
            if (inclusionMask != null && !inclusionMask[index]) {
                continue;
            }
            ChatMessage message = conversationMessages.get(index);
            if (!isRemovableMessage(message)) {
                continue;
            }
            total += tokenEstimator.estimateTokenCountInMessage(message);
        }
        return total;
    }

    private int firstProtectedTurnStartIndex(int conversationEndIndex) {
        List<ActiveTurnRange> ranges = activeTurnRanges();
        if (ranges.isEmpty()) {
            return Math.min(activeStartIndex, conversationEndIndex);
        }
        int protectedCount = Math.min(protectedRecentTurnCount, ranges.size());
        int protectedIndex = ranges.size() - protectedCount;
        return ranges.get(protectedIndex).startIndex;
    }

    private List<HistoricalToolCycle> collectHistoricalToolCycles(int historicalEndIndex) {
        List<HistoricalToolCycle> cycles = new ArrayList<>();
        int startIndex = Math.min(activeStartIndex, historicalEndIndex);
        for (int index = startIndex; index < historicalEndIndex; index++) {
            ChatMessage message = conversationMessages.get(index);
            if (!isToolRequestMessage(message)) {
                continue;
            }
            int cycleEndIndex = index + 1;
            long tokenCount = tokenEstimator.estimateTokenCountInMessage(message);
            while (cycleEndIndex < historicalEndIndex) {
                ChatMessage nextMessage = conversationMessages.get(cycleEndIndex);
                if (nextMessage instanceof ToolExecutionResultMessage) {
                    tokenCount += tokenEstimator.estimateTokenCountInMessage(nextMessage);
                    cycleEndIndex++;
                    continue;
                }
                if (nextMessage instanceof ToolCallSummaryMessage) {
                    cycleEndIndex++;
                    continue;
                }
                break;
            }
            cycles.add(new HistoricalToolCycle(index, cycleEndIndex, tokenCount));
            index = cycleEndIndex - 1;
        }
        return cycles;
    }

    private List<HistoricalToolCycle> trimHistoricalToolCycles(List<HistoricalToolCycle> historicalCycles,
                                                               long historicalToolTokenCap) {
        List<HistoricalToolCycle> hiddenCycles = new ArrayList<>();
        long visibleHistoricalToolTokens = 0L;
        for (HistoricalToolCycle cycle : historicalCycles) {
            visibleHistoricalToolTokens += cycle.tokenCount();
        }
        for (HistoricalToolCycle cycle : historicalCycles) {
            if (visibleHistoricalToolTokens <= historicalToolTokenCap) {
                break;
            }
            hiddenCycles.add(cycle);
            visibleHistoricalToolTokens -= cycle.tokenCount();
        }
        return hiddenCycles;
    }

    private boolean replaceHiddenHistoricalToolCycles(List<HistoricalToolCycle> hiddenCycles) {
        if (sameHistoricalToolCycles(hiddenHistoricalToolCycles, hiddenCycles)) {
            return false;
        }
        hiddenHistoricalToolCycles.clear();
        hiddenHistoricalToolCycles.addAll(hiddenCycles);
        return true;
    }

    private boolean sameHistoricalToolCycles(List<HistoricalToolCycle> first, List<HistoricalToolCycle> second) {
        if (first.size() != second.size()) {
            return false;
        }
        for (int index = 0; index < first.size(); index++) {
            HistoricalToolCycle firstCycle = first.get(index);
            HistoricalToolCycle secondCycle = second.get(index);
            if (firstCycle.startIndex() != secondCycle.startIndex()
                || firstCycle.endIndex() != secondCycle.endIndex()
                || firstCycle.tokenCount() != secondCycle.tokenCount()) {
                return false;
            }
        }
        return true;
    }

    private boolean isToolRequestMessage(ChatMessage message) {
        if (!(message instanceof AiMessage) || message instanceof InstructionAckMessage) {
            return false;
        }
        return ((AiMessage) message).hasToolExecutionRequests();
    }

    private double validateHistoricalToolTokenShare(double share) {
        if (share < 0.0d || share > 1.0d) {
            throw new IllegalArgumentException("historicalToolTokenShare must be between 0.0 and 1.0");
        }
        return share;
    }

    private static class ActiveTurnRange {
        private final int startIndex;
        private final int endIndex;

        private ActiveTurnRange(int startIndex, int endIndex) {
            this.startIndex = startIndex;
            this.endIndex = endIndex;
        }
    }

    private static class HistoricalToolCycle {
        private final int startIndex;
        private final int endIndex;
        private final long tokenCount;

        private HistoricalToolCycle(int startIndex, int endIndex, long tokenCount) {
            this.startIndex = startIndex;
            this.endIndex = endIndex;
            this.tokenCount = tokenCount;
        }

        private int startIndex() {
            return startIndex;
        }

        private int endIndex() {
            return endIndex;
        }

        private long tokenCount() {
            return tokenCount;
        }
    }

    private static class VisibleContextSelection {
        private final int visibleStartIndex;
        private final int firstVisibleHistoryIndex;
        private final boolean[] inclusionMask;
        private final List<HistoricalToolCycle> hiddenHistoricalToolCycles;
        private final long visibleTokenCount;

        private VisibleContextSelection(int visibleStartIndex,
                                        int firstVisibleHistoryIndex,
                                        boolean[] inclusionMask,
                                        List<HistoricalToolCycle> hiddenHistoricalToolCycles,
                                        long visibleTokenCount) {
            this.visibleStartIndex = visibleStartIndex;
            this.firstVisibleHistoryIndex = firstVisibleHistoryIndex;
            this.inclusionMask = inclusionMask;
            this.hiddenHistoricalToolCycles = hiddenHistoricalToolCycles;
            this.visibleTokenCount = visibleTokenCount;
        }

        private int firstVisibleHistoryIndex() {
            return firstVisibleHistoryIndex;
        }

        private boolean[] inclusionMask() {
            return inclusionMask;
        }

        private boolean includes(int index) {
            return index >= visibleStartIndex
                && index < inclusionMask.length
                && inclusionMask[index];
        }

        private List<HistoricalToolCycle> hiddenHistoricalToolCycles() {
            return hiddenHistoricalToolCycles;
        }

        private long visibleTokenCount() {
            return visibleTokenCount;
        }
    }

    private static class ChatTokenEstimator {
        private static final String FALLBACK_MODEL_NAME = "gpt-4o-mini";

        private final Supplier<String> modelNameProvider;
        private OpenAiTokenCountEstimator estimator;
        private String activeModelName;

        private ChatTokenEstimator(Supplier<String> modelNameProvider) {
            this.modelNameProvider = modelNameProvider == null ? () -> null : modelNameProvider;
        }

        int estimateTokenCountInMessage(ChatMessage message) {
            OpenAiTokenCountEstimator activeEstimator = estimator();
            try {
                return activeEstimator.estimateTokenCountInMessage(message);
            } catch (RuntimeException error) {
                return 0;
            }
        }

        private OpenAiTokenCountEstimator estimator() {
            String modelName = normalizeModelName(modelNameProvider.get());
            if (estimator == null || !modelName.equals(activeModelName)) {
                estimator = buildEstimator(modelName);
                activeModelName = modelName;
            }
            return estimator;
        }

        private OpenAiTokenCountEstimator buildEstimator(String modelName) {
            try {
                return new OpenAiTokenCountEstimator(modelName);
            } catch (IllegalArgumentException error) {
                return new OpenAiTokenCountEstimator(FALLBACK_MODEL_NAME);
            }
        }

        private String normalizeModelName(String modelName) {
            if (modelName == null || modelName.trim().isEmpty()) {
                return FALLBACK_MODEL_NAME;
            }
            String normalized = modelName.trim();
            int slashIndex = normalized.lastIndexOf('/');
            if (slashIndex >= 0 && slashIndex < normalized.length() - 1) {
                normalized = normalized.substring(slashIndex + 1);
            }
            return normalized;
        }
    }
}
