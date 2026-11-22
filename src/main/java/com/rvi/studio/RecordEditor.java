package com.rvi.studio;

import com.rvi.domain.NDefContact;
import com.rvi.domain.NDefContent;
import com.rvi.domain.NDefText;
import com.rvi.domain.NDefUri;
import com.rvi.domain.NDefWifi;
import com.rvi.domain.WifiAuthentication;
import com.rvi.domain.WifiEncryption;
import com.rvi.domain.exception.NDefValueException;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Control;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.Optional;
import java.util.function.Supplier;

final class RecordEditor
{
    private static final int MAX_FIELD_LENGTH = 1024;

    private final VBox fields = new VBox(10);
    private Control firstField;

    static void style(final DialogPane pane)
    {
        pane.getStylesheets().add(SocialCardApp.class.getResource("theme.css").toExternalForm());
    }

    static Optional<NDefContent> show(final Window owner, final RecordKind kind, final NDefContent existing)
    {
        final RecordEditor editor = new RecordEditor();

        if (existing == null)
        {
            return editor.open(owner, kind, null, "Add " + kind, "Add");
        }

        return editor.open(owner, kind, existing, "Edit " + kind, "Save");
    }

    private Optional<NDefContent> open(final Window owner, final RecordKind kind, final NDefContent existing,
                                       final String heading, final String action)
    {
        final ButtonType apply = new ButtonType(action, ButtonBar.ButtonData.OK_DONE);
        final Dialog<NDefContent> dialog = createDialog(owner, heading, apply);
        final DialogPane pane = dialog.getDialogPane();
        final Label errorLabel = new Label();
        final ScrollPane scroll = new ScrollPane(fields);
        final Node button = pane.lookupButton(apply);
        final Supplier<NDefContent> build = switch (kind)
        {
            case LINK -> link((NDefUri) existing);
            case TEXT -> text((NDefText) existing);
            case CONTACT -> contact((NDefContact) existing);
            case WIFI -> wifi((NDefWifi) existing);
        };

        errorLabel.getStyleClass().add("error-text");
        errorLabel.setWrapText(true);
        errorLabel.managedProperty().bind(errorLabel.visibleProperty());
        errorLabel.setVisible(false);

        fields.getChildren().add(errorLabel);
        fields.setPadding(new Insets(4, 2, 8, 2));

        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefViewportWidth(380);
        scroll.setMaxHeight(440);
        pane.setContent(scroll);

        button.getStyleClass().add("primary-action");
        button.addEventFilter(ActionEvent.ACTION, event ->
        {
            event.consume();

            try
            {
                dialog.setResult(build.get());
                dialog.close();
            }
            catch (IllegalArgumentException | NDefValueException error)
            {
                errorLabel.setText(StudioController.errorMessage("Invalid input", error));
                errorLabel.setVisible(true);
                scroll.setVvalue(1);
            }
        });

        dialog.setOnShown(event -> Platform.runLater(firstField::requestFocus));
        return dialog.showAndWait();
    }

    private static Dialog<NDefContent> createDialog(final Window owner, final String heading, final ButtonType apply)
    {
        final Dialog<NDefContent> dialog = new Dialog<>();
        final DialogPane pane = dialog.getDialogPane();

        dialog.initOwner(owner);
        dialog.setTitle("SocialCard Studio");
        dialog.setHeaderText(heading);
        dialog.setResizable(true);
        dialog.setResultConverter(buttonType -> null);

        style(pane);
        pane.getButtonTypes().setAll(StudioController.CANCEL, apply);

        return dialog;
    }

    private Supplier<NDefContent> link(final NDefUri existing)
    {
        final TextField uri = textField("Address", "https://example.com");

        if (existing != null)
        {
            uri.setText(existing.uri());
        }

        return () -> new NDefUri(uri.getText().strip());
    }

    private Supplier<NDefContent> text(final NDefText existing)
    {
        final TextArea body = new TextArea();
        final TextField language = new TextField("en");

        body.setWrapText(true);
        body.setPrefRowCount(4);
        field("Text", body);

        language.setPromptText("en");
        field("Language code", language);

        if (existing != null)
        {
            body.setText(existing.text());
            language.setText(existing.language());
        }

        return () -> new NDefText(body.getText(), language.getText().strip());
    }

    private Supplier<NDefContent> contact(final NDefContact existing)
    {
        final TextField firstName = textField("First name", "");
        final TextField lastName = textField("Last name", "");
        final TextField email = textField("Email", "");
        final TextField phone = textField("Phone", "");
        final TextField website = textField("Website", "https://example.com");

        if (existing != null)
        {
            firstName.setText(existing.firstName());
            lastName.setText(existing.lastName());
            email.setText(existing.email());
            phone.setText(existing.phone());
            website.setText(existing.website());
        }

        return () -> new NDefContact(firstName.getText(), lastName.getText(), email.getText(),
                phone.getText(), website.getText());
    }

    private Supplier<NDefContent> wifi(final NDefWifi existing)
    {
        final TextField ssid = textField("Network name (SSID)", "");
        final CheckBox open = new CheckBox("Open network");
        final PasswordField password = new PasswordField();
        final Label note = new Label("Anyone who scans the tag can read the Wi-Fi credentials");

        if (existing != null)
        {
            ssid.setText(existing.ssid());
            open.setSelected(existing.authentication() == WifiAuthentication.OPEN);
            password.setText(existing.password());
        }

        fields.getChildren().add(open);
        password.disableProperty().bind(open.selectedProperty());
        field("Password", password);

        note.getStyleClass().add("muted");
        note.setWrapText(true);
        fields.getChildren().add(note);

        return () ->
        {
            if (open.isSelected())
            {
                return new NDefWifi(ssid.getText(), WifiAuthentication.OPEN, WifiEncryption.NONE, "");
            }

            return new NDefWifi(ssid.getText(), WifiAuthentication.WPA2_PERSONAL,
                    WifiEncryption.AES, password.getText());
        };
    }

    private TextField textField(final String label, final String prompt)
    {
        final TextField input = new TextField();

        input.setPromptText(prompt);
        field(label, input);

        return input;
    }

    private void field(final String label, final Control input)
    {
        final Label caption = new Label(label);
        final VBox row = new VBox(4, caption, input);

        if (input instanceof final TextInputControl text)
        {
            text.setTextFormatter(new TextFormatter<>(change ->
            {
                final int length = change.getControlText().length()
                        - (change.getRangeEnd() - change.getRangeStart()) + change.getText().length();

                if (length > MAX_FIELD_LENGTH)
                {
                    return null;
                }

                return change;
            }));
        }

        caption.setLabelFor(input);
        fields.getChildren().add(row);

        if (firstField == null)
        {
            firstField = input;
        }
    }
}
