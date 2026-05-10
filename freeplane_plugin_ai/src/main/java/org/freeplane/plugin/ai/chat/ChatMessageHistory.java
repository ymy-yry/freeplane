package org.freeplane.plugin.ai.chat;

import org.freeplane.core.util.LogUtils;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import javax.swing.JEditorPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.HTMLWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class ChatMessageHistory {
    private final JEditorPane messageHistoryPane;
    private final HTMLEditorKit messageHistoryEditorKit;
    private final List<MessageEntry> messageEntries;

    ChatMessageHistory(JEditorPane messageHistoryPane, HTMLEditorKit messageHistoryEditorKit) {
        this.messageHistoryPane = messageHistoryPane;
        this.messageHistoryEditorKit = messageHistoryEditorKit;
        messageEntries = new ArrayList<>();
    }

    void appendMessage(String sourceText, String messageText, String styleClassName) {
        HTMLDocument document = (HTMLDocument) messageHistoryPane.getDocument();
        String messageMarkup = "<div class=\"" + styleClassName + "\">" + messageText + "</div>";
        int startOffset = document.getLength();
        try {
            messageHistoryEditorKit.insertHTML(document, document.getLength(), messageMarkup, 0, 0, null);
        } catch (BadLocationException | IOException error) {
            LogUtils.severe(error);
        }
        int endOffset = document.getLength();
        messageEntries.add(new MessageEntry(startOffset, endOffset, sourceText, messageMarkup, styleClassName));
        scrollToBottom();
    }

    void clear() {
        messageHistoryPane.setText("<html><body></body></html>");
        messageHistoryPane.setCaretPosition(0);
        messageEntries.clear();
    }

    int size() {
        return messageEntries.size();
    }

    Transferable createTransferable(int selectionStart, int selectionEnd) {
        if (selectionStart == selectionEnd) {
            return null;
        }
        int documentLength = messageHistoryPane.getDocument().getLength();
        if (selectionStart <= 0 && selectionEnd >= documentLength) {
            return createEntryTransferable(selectionStart, selectionEnd);
        }
        Transferable selectionTransferable = createSelectionTransferable(selectionStart, selectionEnd);
        if (selectionTransferable != null) {
            return selectionTransferable;
        }
        return createEntryTransferable(selectionStart, selectionEnd);
    }

    private Transferable createSelectionTransferable(int selectionStart, int selectionEnd) {
        HTMLDocument document = (HTMLDocument) messageHistoryPane.getDocument();
        int selectionLength = selectionEnd - selectionStart;
        try {
            String selectedPlainText = document.getText(selectionStart, selectionLength);
            MessageEntry containingEntry = findContainingEntry(selectionStart, selectionEnd);
            String selectedMarkupText = buildSelectionMarkup(document, selectionStart, selectionLength, containingEntry);
            if (selectedMarkupText == null) {
                return null;
            }
            return new ChatMessageTransferable(selectedPlainText, wrapMarkup(selectedMarkupText));
        } catch (BadLocationException error) {
            return null;
        }
    }

    private String buildSelectionMarkup(HTMLDocument document, int selectionStart, int selectionLength,
                                        MessageEntry containingEntry) {
        try {
            StringWriter stringWriter = new StringWriter();
            HTMLWriter markupWriter = new HTMLWriter(
                stringWriter,
                document,
                selectionStart,
                selectionLength);
            markupWriter.write();
            String markup = stringWriter.toString();
            if (containingEntry == null) {
                return markup;
            }
            return stripOuterMessageDiv(markup, containingEntry);
        } catch (IOException | BadLocationException error) {
            return null;
        }
    }

    private Transferable createEntryTransferable(int selectionStart, int selectionEnd) {
        List<MessageEntry> selectedEntries = new ArrayList<>();
        for (MessageEntry entry : messageEntries) {
            if (selectionStart < entry.endOffset && selectionEnd > entry.startOffset) {
                selectedEntries.add(entry);
            }
        }
        if (selectedEntries.isEmpty()) {
            return null;
        }
        String plainText = joinSourceText(selectedEntries);
        String markupText = wrapMarkup(joinMarkup(selectedEntries));
        return new ChatMessageTransferable(plainText, markupText);
    }

    private MessageEntry findContainingEntry(int selectionStart, int selectionEnd) {
        for (MessageEntry entry : messageEntries) {
            if (selectionStart >= entry.startOffset && selectionEnd <= entry.endOffset) {
                return entry;
            }
        }
        return null;
    }

    private String stripOuterMessageDiv(String markup, MessageEntry entry) {
        if (markup == null || entry == null || entry.styleClassName == null) {
            return markup;
        }
        String closingTag = "</div>";
        Pattern openingPattern = Pattern.compile("<div\\s+[^>]*class=(\"|')?[^>]*\\b"
            + Pattern.quote(entry.styleClassName) + "\\b[^>]*>",
            Pattern.CASE_INSENSITIVE);
        Matcher openingMatcher = openingPattern.matcher(markup);
        if (!openingMatcher.find()) {
            return markup;
        }
        int openingIndex = openingMatcher.start();
        int openingEndIndex = openingMatcher.end();
        int closingIndex = markup.lastIndexOf(closingTag);
        if (closingIndex < 0 || closingIndex <= openingIndex) {
            return markup;
        }
        String before = markup.substring(0, openingIndex);
        String inner = markup.substring(openingEndIndex, closingIndex);
        String after = markup.substring(closingIndex + closingTag.length());
        return before + inner + after;
    }

    private String joinSourceText(List<MessageEntry> selectedEntries) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < selectedEntries.size(); index++) {
            if (index > 0) {
                builder.append("\n\n");
            }
            builder.append(selectedEntries.get(index).sourceText);
        }
        return builder.toString();
    }

    private String joinMarkup(List<MessageEntry> selectedEntries) {
        StringBuilder builder = new StringBuilder();
        for (MessageEntry entry : selectedEntries) {
            builder.append(entry.messageMarkup);
        }
        return builder.toString();
    }

    private String wrapMarkup(String markup) {
        return "<html><body>" + markup + "</body></html>";
    }

    private void scrollToBottom() {
        messageHistoryPane.setCaretPosition(messageHistoryPane.getDocument().getLength());
    }

    private static class MessageEntry {
        private final int startOffset;
        private final int endOffset;
        private final String sourceText;
        private final String messageMarkup;
        private final String styleClassName;

        MessageEntry(int startOffset, int endOffset, String sourceText, String messageMarkup, String styleClassName) {
            this.startOffset = startOffset;
            this.endOffset = endOffset;
            this.sourceText = sourceText;
            this.messageMarkup = messageMarkup;
            this.styleClassName = styleClassName;
        }
    }

    private static class ChatMessageTransferable implements Transferable {
        private static final DataFlavor MARKUP_DATA_FLAVOR = createMarkupDataFlavor();

        private final String plainText;
        private final String markupText;

        ChatMessageTransferable(String plainText, String markupText) {
            this.plainText = plainText;
            this.markupText = markupText;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[] { DataFlavor.stringFlavor, MARKUP_DATA_FLAVOR };
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.stringFlavor.equals(flavor) || MARKUP_DATA_FLAVOR.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (DataFlavor.stringFlavor.equals(flavor)) {
                return plainText;
            }
            if (MARKUP_DATA_FLAVOR.equals(flavor)) {
                return markupText;
            }
            throw new UnsupportedFlavorException(flavor);
        }

        private static DataFlavor createMarkupDataFlavor() {
            try {
                return new DataFlavor("text/html;class=java.lang.String");
            } catch (ClassNotFoundException exception) {
                throw new IllegalStateException("Unable to create text/html data flavor.", exception);
            }
        }
    }

}
