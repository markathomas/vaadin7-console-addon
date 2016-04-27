package org.vaadin7.console.client;

import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.InputElement;
import com.google.gwt.dom.client.Node;
import com.google.gwt.dom.client.Style.Display;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.FocusWidget;
import com.vaadin.client.Util;

import java.util.ArrayList;
import java.util.List;

/**
 * GWT Console Widget.
 *
 * @author Sami Ekblad / Vaadin
 */
public class TextConsole extends FocusWidget {

    /* Control characters in http://en.wikipedia.org/wiki/Control_character */

    public static final char CTRL_BELL = 'G';
    public static final char CTRL_BACKSPACE = 'H';
    public static final char CTRL_TAB = 'I';
    public static final char CTRL_LINE_FEED = 'J';
    public static final char CTRL_FORM_FEED = 'L';
    public static final char CTRL_CARRIAGE_RETURN = 'M';
    public static final char CTRL_ESCAPE = '[';
    public static final char CTRL_DELETE = '?';

    private static final char[] CTRL = {CTRL_BELL, CTRL_BACKSPACE, CTRL_TAB, CTRL_LINE_FEED, CTRL_FORM_FEED, CTRL_CARRIAGE_RETURN, CTRL_ESCAPE, CTRL_DELETE};

    public static char getControlKey(final int kc) {
        for (final char c : CTRL) {
            if (kc == c) {
                return c;
            }
        }
        return 0;
    }

    private static final String DEFAULT_TABS = "    ";
    private static final int BIG_NUMBER = 100000;
    private final DivElement term;
    private TextConsoleConfig config;
    private TextConsoleHandler handler;
    private final Element buffer;
    private final Element prompt;
    private final Element promptTxt;
    private final InputElement input;
    private List<String> cmdHistory = new ArrayList<String>();
    private int cmdHistoryIndex = -1;
    private HandlerRegistration clickHandlerRegistration;
    private HandlerRegistration keyHandlerRegistration;
    private int fontW = -1;
    private int fontH = -1;
    private int scrollbarW = -1;
    private int rows;
    private int cols;
    private String tabs = DEFAULT_TABS;
    private boolean focused;
    private int promptRows;
    private int padding;
    private final DivElement promptWrap;
    private Timer timer;
    private int maxBufferSize;
    private String cleanPs;
    private int paddingW;
    private final ClickHandler clickHandler = new ClickHandler() {

        public void onClick(final ClickEvent event) {
            setFocus(true);
        }
    };

    private final KeyDownHandler keyHandler = new KeyDownHandler() {

        public void onKeyDown(final KeyDownEvent event) {

            // (re-)show the prompt
            setPromptActive(true);

            if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
                event.preventDefault();
                carriageReturn();
            } else if (event.getNativeKeyCode() == KeyCodes.KEY_UP || event.getNativeKeyCode() == KeyCodes.KEY_DOWN) {
                event.preventDefault();
                handleCommandHistoryBrowse(event.getNativeKeyCode());
            } else if (event.getNativeKeyCode() == KeyCodes.KEY_TAB) {
                event.preventDefault();
                suggest();
            } else if (event.getNativeKeyCode() == KeyCodes.KEY_BACKSPACE && getInputLenght() == 0) {
                bell();
            } else if (event.getNativeEvent().getCtrlKey()) {
                final char ctrlChar = getControlKey(event.getNativeKeyCode());
                if (ctrlChar > 0) {
                    event.preventDefault();
                    handleControlChar(ctrlChar);
                }
            }

        }
    };

    public TextConsole() {

        // Main element
        term = Document.get().createDivElement();
        term.addClassName("term");
        setElement(term);
        setTabIndex(0);

        // Buffer
        buffer = DOM.createDiv();
        buffer.addClassName("b");
        term.appendChild(buffer);

        // Prompt elements
        promptWrap = Document.get().createDivElement();
        promptWrap.addClassName("pw");
        term.appendChild(promptWrap);

        prompt = DOM.createDiv();
        promptWrap.appendChild(prompt);
        prompt.addClassName("p");

        promptTxt = DOM.createDiv();
        promptTxt.addClassName("ps");

        Element txtFieldContainer = DOM.createDiv();
        txtFieldContainer.addClassName("iw");
        input = (InputElement) Document.get().createElement("input");
        txtFieldContainer.appendChild(input);
        input.addClassName("i");
        input.setTabIndex(-1);
        input.setAttribute("spellcheck", "false");

        prompt.appendChild(promptTxt);
        prompt.appendChild(txtFieldContainer);
        config = TextConsoleConfig.newInstance();

        setPromptActive(false);

        updateFontDimensions();
    }

    protected int getInputLenght() {
        final String v = input.getValue();
        if (v != null) {
            return v.length();
        }
        return -1;
    }

    protected void handleControlChar(final char c) {
        switch (c) {
            case TextConsole.CTRL_BACKSPACE:
                backspace();
                break;
            case TextConsole.CTRL_BELL:
                bell();
                break;
            case TextConsole.CTRL_CARRIAGE_RETURN:
                carriageReturn();
                break;
            case TextConsole.CTRL_DELETE:
                bell(); // TODO: not supported yet
                break;
            case TextConsole.CTRL_ESCAPE:
                bell(); // TODO: not supported yet
                break;
            case TextConsole.CTRL_FORM_FEED:
                formFeed();
                break;
            case TextConsole.CTRL_LINE_FEED:
                lineFeed();
                break;
            case TextConsole.CTRL_TAB:
                tab();
                break;

            default:
                bell();
                break;
        }
    }

    protected void suggest() {
        handler.suggest(getInput());
    }

    protected void handleCommandHistoryBrowse(final int i) {
        cmdHistoryIndex = i == KeyCodes.KEY_UP ? cmdHistoryIndex - 1 : cmdHistoryIndex + 1;
        if (cmdHistoryIndex >= 0 && cmdHistoryIndex < cmdHistory.size()) {
            prompt(cmdHistory.get(cmdHistoryIndex));
        } else {
            prompt();
        }
    }

    public String getInput() {
        return input.getValue();
    }

    protected void setInput(final String inputText) {
        if (inputText != null) {
            input.setValue(inputText);
        } else {
            input.setValue("");
        }
        if (isFocused()) {
            focusPrompt();
        }
    }

    public void lineFeed() {
        carriageReturn();
    }

    protected void tab() {
        prompt(getInput() + "\t");
    }

    protected void backspace() {
        bell();
    }

    protected void carriageReturn() {
        if (config.isPrintPromptOnInput()) {
            setPromptActive(false);
            print(getCurrentPromptContent());
        }
        String lineBuffer = getInput();
        if (config.shouldTrim()) lineBuffer = lineBuffer.trim();
        if (!"".equals(lineBuffer)) {
            cmdHistory.add(lineBuffer);
            cmdHistoryIndex = cmdHistory.size();
        }
        if (handler != null) {
            handler.terminalInput(this, lineBuffer);
        }
    }

    private void setPromptActive(final boolean active) {
        if (active && !isPromptActive()) {
            prompt.getStyle().setProperty("display", "flex");
        } else if (!active && isPromptActive()) {
            prompt.getStyle().setDisplay(Display.NONE);
        }
    }

    private boolean isPromptActive() {
        return !Display.NONE.getCssName().equals(prompt.getStyle().getDisplay());
    }

    private boolean isFocused() {
        return focused;
    }

    public void init() {
        scrollbarW = getScrollbarWidth();
        final String padStr = term.getStyle().getPadding();
        if (padStr != null && padStr.endsWith("px")) {
            padding = Integer.parseInt(padStr.substring(0, padStr.length() - 2));
        } else {
            padding = 1;
            paddingW = 2;
        }

        setPromptTxt(config.getPs());
        setMaxBufferSize(config.getMaxBufferSize());
        prompt();
    }

    private void updateFontDimensions() {

        // Test element for font size
        DivElement test = Document.get().createDivElement();
        test.setAttribute("style", "position: absolute;");
        test.setInnerHTML("X");
        term.appendChild(test);

        fontW = test.getClientWidth();
        fontH = test.getClientHeight();
        if (fontW <= 0 || fontW > 100) {
            fontW = test.getOffsetWidth();
        }
        if (fontH <= 0 || fontH > 100) {
            fontH = test.getOffsetHeight();
        }
        if (fontW <= 0 || fontW > 100) {
            fontW = 1;
        }
        if (fontH <= 0 || fontH > 100) {
            fontH = 1;
        }
        term.removeChild(test);
    }

    public TextConsoleConfig getConfig() {
        return config;
    }

    public void focusPrompt() {
        focusPrompt(-1);
    }

    public void focusPrompt(final int cursorPos) {
        input.focus();

        // Focus to end
        final String s = getInput();
        if (s != null && s.length() > 0) {
            setSelectionRange(input, s.length(), s.length());
        }
    }

    private native void setSelectionRange(Element input, int selectionStart, int selectionEnd)/*-{
        if (input.setSelectionRange) {
            input.focus();
            input.setSelectionRange(selectionStart, selectionEnd);
        }
        else if (input.createTextRange) {
            var range = input.createTextRange();
            range.collapse(true);
            range.moveEnd('character', selectionEnd);
            range.moveStart('character', selectionStart);
            range.select();
        }
    }-*/;

    private native int getScrollbarWidth()/*-{

        var i = $doc.createElement('p');
        i.style.width = '100%';
        i.style.height = '200px';
        var o = $doc.createElement('div');
        o.style.position = 'absolute';
        o.style.top = '0px';
        o.style.left = '0px';
        o.style.visibility = 'hidden';
        o.style.width = '200px';
        o.style.height = '150px';
        o.style.overflow = 'hidden';
        o.appendChild(i);
        $doc.body.appendChild(o);
        var w1 = i.offsetWidth;
        var h1 = i.offsetHeight;
        o.style.overflow = 'scroll';
        var w2 = i.offsetWidth;
        var h2 = i.offsetHeight;
        if (w1 == w2) w2 = o.clientWidth;
        if (h1 == h2) h2 = o.clientWidth;
        $doc.body.removeChild(o);
        return w1 - w2;
    }-*/;

    protected void setPromptTxt(final String string) {
        config.setPs(string);
        cleanPs = Util.escapeHTML(string);
        cleanPs = cleanPs.replaceAll(" ", "&nbsp;");
        promptTxt.setInnerHTML(cleanPs);
    }

    public void prompt(final String inputText) {
        setPromptActive(true);
        promptTxt.setInnerHTML(cleanPs);
        setInput(inputText);
        scrollToEnd();
    }

    public void focusInput() {
        if (isFocused())
            setPromptActive(true);
        scrollToEnd();
        promptTxt.setInnerHTML(cleanPs);
    }

    private boolean isCheckedScrollState = false;

    public void scrollToEnd() {
        if (term.getOffsetHeight() < prompt.getScrollHeight() + prompt.getOffsetTop()) {
            term.setScrollTop(prompt.getOffsetTop() - (term.getOffsetHeight() - prompt.getScrollHeight()));
        }
    }

    private void beforeChangeTerminal() {
        if (!isCheckedScrollState) {
            config.setScrolledToEnd(term.getScrollTop() >= term.getScrollHeight() - term.getClientHeight());
            isCheckedScrollState = true;
        }
    }

    public void prompt() {
        prompt(null);
    }

    public void print(String string) {
        printWithClass(string, null);
    }

    public void printWithClass(String string, String className) {
        beforeChangeTerminal();

        if (string == null)
            string = "";
        if (string.equals("") && input.getValue().equals(""))
            return;
        string = string.replaceAll("\t", tabs);
        if (isPromptActive()) {
            setPromptActive(false);
            string = getCurrentPromptContent() + string;
        }

        appendLine(buffer, string, -1, className);
    }

    public void append(String string) {
        print(string);
    }

    public void appendWithClass(String string, String className) {
        printWithClass(string, className);
    }

    private String getCurrentPromptContent() {
        return promptTxt.getInnerText() + getInput();
    }

    private void reducePrompt(final int rows) {
        int newRows = promptRows - rows;
        if (newRows < 1) {
            newRows = 1;
        }
        setPromptHeight(newRows);
    }

    private void setPromptHeight(final int rows) {
        final int min = 1;
        final int max = getRows();
        promptRows = rows < min ? min : (rows > max ? max : rows);
        final int newHeight = fontH * rows;
        promptWrap.getStyle().setHeight(newHeight, Unit.PX);
    }

    /**
     * Split long text based on length.
     *
     * @param parent
     * @param str
     * @param maxLine
     * @return
     */
    private int appendLine(final Node parent, String str, final int maxLine, String className) {
        Element e = DOM.createDiv();
        e.setInnerHTML(str.replaceAll("\\n", "<br/>"));
        e.addClassName("bh");
        if (className != null)
            e.addClassName(className);
        parent.appendChild(e);
        checkBufferLimit();

        return 1;
    }

    private void checkBufferLimit() {

        // Buffer means only offscreen lines
        while (buffer.getChildCount() > maxBufferSize) {
            buffer.removeChild(buffer.getFirstChild());
        }

    }

    public void println(final String string) {
        print(string + "\n");
    }

    public void printlnWithClass(final String string, final String className) {
        printWithClass(string + "\n", className);
    }

    @Override
    public void setHeight(final String height) {
        final int oldh = term.getClientHeight();
        super.setHeight(height);
        final int newh = term.getClientHeight();
        if (newh != oldh) {
            calculateRowsFromHeight();
        }
    }

    protected void calculateRowsFromHeight() {
        final int h = term.getClientHeight() - (2 * padding);
        rows = h / fontH;
        config.setRows(rows);
    }

    protected void calculateColsFromWidth() {
        final int w = term.getClientWidth();
        cols = (w - 2 * paddingW) / fontW;
        config.setCols(cols);
        buffer.getStyle().setWidth((cols * fontW), Unit.PX);
        prompt.getStyle().setWidth((cols * fontW), Unit.PX);
    }

    @Override
    public void setWidth(final String width) {
        final int oldw = term.getClientWidth();
        super.setWidth(width);
        final int neww = term.getClientWidth();
        if (neww != oldw) {
            calculateColsFromWidth();
        }
    }

    private native int trace()/*-{
        console.trace();
    }-*/;

    @Override
    public void setFocus(final boolean focused) {
        this.focused = focused;
        super.setFocus(focused);
        if (focused) {
            focusPrompt();
        }
    }

    public void setHandler(final TextConsoleHandler handler) {
        this.handler = handler;

    }

    public int getRows() {
        return rows;
    }

    public void reset() {
        beforeChangeTerminal();
        setPromptActive(false);
        clearBuffer();
        print(config.getGreeting());
        prompt();
    }

    public int getBufferSize() {
        return (buffer.getClientHeight() / fontH);
    }

    public int getMaxBufferSize() {
        return maxBufferSize;
    }

    public void setMaxBufferSize(final int maxBuffer) {
        maxBufferSize = maxBuffer > 0 ? maxBuffer : 0;
        checkBufferLimit();
    }

    public void clearBuffer() {
        buffer.removeAllChildren();
    }

    public void formFeed() {
        scrollToEnd();

        checkBufferLimit();
    }

    protected void clearCommandHistory() {
        cmdHistory = new ArrayList<String>();
        cmdHistoryIndex = -1;
    }

    public String getHeight() {
        return (term.getClientHeight() - 2 * padding) + "px";
    }

    public String getWidth() {
        return (term.getClientWidth() + scrollbarW - 2 * paddingW) + "px";
    }

    protected void bell() {
        // Clear previous
        if (timer != null) {
            timer.cancel();
            timer = null;
            term.removeClassName("term-rev");
            input.removeClassName("term-rev");
        }
        // Add styles and start the timer
        input.addClassName("term-rev");
        term.addClassName("term-rev");
        timer = new Timer() {

            @Override
            public void run() {
                term.removeClassName("term-rev");
                input.removeClassName("term-rev");
            }
        };
        timer.schedule(150);
    }

    // Add history only once
    protected void addPreviousHistory(List<String> history) {
        if (!cmdHistory.isEmpty() || history.isEmpty())
            return;
        cmdHistory.addAll(history);
        cmdHistoryIndex = cmdHistory.size();
        if (maxBufferSize < 1)
            maxBufferSize = cmdHistory.size();

        setPromptActive(false);
        for (String command : history) {
            print(promptTxt.getInnerText() + command);
        }
        setPromptActive(true);
        promptWrap.scrollIntoView();
    }

    @Override
    protected void onUnload() {
        super.onUnload();

        if (clickHandlerRegistration != null) {
            clickHandlerRegistration.removeHandler();
        }
        if (keyHandlerRegistration != null) {
            keyHandlerRegistration.removeHandler();
        }
    }

    @Override
    protected void onLoad() {
        super.onLoad();
        clickHandlerRegistration = addDomHandler(clickHandler, ClickEvent.getType());
        keyHandlerRegistration = addDomHandler(keyHandler, KeyDownEvent.getType());

        init();
    }
}
