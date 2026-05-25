package atri.palaash.jforge.ui;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * A streamlined version of Swing's {@link DocumentListener} that merges
 * all three callback methods into a single {@code update} method.
 * <p>
 * Instead of writing three separate methods you can use a single lambda:
 * <pre>{@code
 * textField.getDocument().addDocumentListener((SimpleDocumentListener) e -> handleChange());
 * }</pre>
 */
@FunctionalInterface
public interface SimpleDocumentListener extends DocumentListener {

    /**
     * Invoked whenever the document's content changes (text inserted, removed, or styled).
     *
     * @param event details about the document change
     */
    void update(DocumentEvent event);

    /**
     * Delegates to {@link #update(DocumentEvent)} when text is inserted.
     *
     * @param event details about the insert event
     */
    @Override
    default void insertUpdate(DocumentEvent event) {
        update(event);
    }

    /**
     * Delegates to {@link #update(DocumentEvent)} when text is removed.
     *
     * @param event details about the removal event
     */
    @Override
    default void removeUpdate(DocumentEvent event) {
        update(event);
    }

    /**
     * Delegates to {@link #update(DocumentEvent)} when a document attribute or style changes.
     *
     * @param event details about the change event
     */
    @Override
    default void changedUpdate(DocumentEvent event) {
        update(event);
    }
}
