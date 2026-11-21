package com.rvi.studio;

import com.rvi.domain.NDefContact;
import com.rvi.domain.NDefContent;
import com.rvi.domain.NDefText;
import com.rvi.domain.NDefUri;
import com.rvi.domain.NDefWifi;
import com.rvi.domain.WifiAuthentication;
import com.rvi.domain.WifiEncryption;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.Optional;
import java.util.function.Supplier;

final class RecordEditor {
    enum Kind {
        LINK("Link"), TEXT("Text"), CONTACT("Kontakt"), WIFI("WLAN");

        private final String title;

        Kind(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    private final VBox fields = new VBox(10);
    private Control firstField;

    static Kind kindOf(NDefContent content) {
        return switch (content) {
            case NDefUri ignored -> Kind.LINK;
            case NDefText ignored -> Kind.TEXT;
            case NDefContact ignored -> Kind.CONTACT;
            case NDefWifi ignored -> Kind.WIFI;
        };
    }

    static void style(DialogPane pane) {
        pane.getStylesheets().add(SocialCardApp.class.getResource("theme.css").toExternalForm());
    }

    static Optional<NDefContent> show(Window owner, Kind kind, NDefContent existing) {
        return new RecordEditor().open(owner, kind, existing);
    }

    private Optional<NDefContent> open(Window owner, Kind kind, NDefContent existing) {
        var dialog = new Dialog<NDefContent>();
        var apply = new ButtonType(existing == null ? "Hinzufügen" : "Übernehmen",
                ButtonBar.ButtonData.OK_DONE);
        dialog.initOwner(owner);
        dialog.setTitle("SocialCard Studio");
        dialog.setHeaderText(kind + (existing == null ? " hinzufügen" : " bearbeiten"));
        dialog.setResizable(true);
        style(dialog.getDialogPane());
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, apply);

        Supplier<NDefContent> build = switch (kind) {
            case LINK -> link((NDefUri) existing);
            case TEXT -> text((NDefText) existing);
            case CONTACT -> contact((NDefContact) existing);
            case WIFI -> wifi((NDefWifi) existing);
        };
        var errorLabel = new Label();
        errorLabel.getStyleClass().add("error-text");
        errorLabel.setWrapText(true);
        errorLabel.managedProperty().bind(errorLabel.visibleProperty());
        errorLabel.setVisible(false);
        fields.getChildren().add(errorLabel);
        fields.setPadding(new Insets(4, 2, 8, 2));
        var scroll = new ScrollPane(fields);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefViewportWidth(380);
        scroll.setMaxHeight(440);
        dialog.getDialogPane().setContent(scroll);

        var button = dialog.getDialogPane().lookupButton(apply);
        button.getStyleClass().add("primary-action");
        button.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            event.consume();
            try {
                dialog.setResult(build.get());
                dialog.close();
            } catch (IllegalArgumentException | com.rvi.domain.exception.NDefValueException error) {
                errorLabel.setText(StudioController.errorMessage("Eingabe ungültig", error));
                errorLabel.setVisible(true);
                scroll.setVvalue(1);
            }
        });
        dialog.setOnShown(event -> Platform.runLater(firstField::requestFocus));
        return dialog.showAndWait();
    }

    private Supplier<NDefContent> link(NDefUri existing) {
        var uri = textField("Adresse", existing == null ? "" : existing.uri(), "https://example.com");
        return () -> new NDefUri(uri.getText().strip());
    }

    private Supplier<NDefContent> text(NDefText existing) {
        var body = new TextArea(existing == null ? "" : existing.text());
        body.setWrapText(true);
        body.setPrefRowCount(4);
        field("Text", body);
        var language = textField("Sprachcode", existing == null ? "de" : existing.language(), "de");
        return () -> new NDefText(body.getText(), language.getText().strip());
    }

    private Supplier<NDefContent> contact(NDefContact existing) {
        var firstName = textField("Vorname", existing == null ? "" : existing.firstName(), "");
        var lastName = textField("Nachname", existing == null ? "" : existing.lastName(), "");
        var email = textField("E-Mail", existing == null ? "" : existing.email(), "");
        var phone = textField("Telefon", existing == null ? "" : existing.phone(), "");
        var website = textField("Website", existing == null ? "" : existing.website(), "https://example.com");
        return () -> new NDefContact(firstName.getText(), lastName.getText(), email.getText(),
                phone.getText(), website.getText());
    }

    private Supplier<NDefContent> wifi(NDefWifi existing) {
        var ssid = textField("Netzwerkname (SSID)", existing == null ? "" : existing.ssid(), "");
        var open = new CheckBox("Offenes Netzwerk");
        open.setSelected(existing != null && existing.authentication() == WifiAuthentication.OPEN);
        fields.getChildren().add(open);
        var password = new PasswordField();
        password.setText(existing == null ? "" : existing.password());
        password.disableProperty().bind(open.selectedProperty());
        field("Passwort", password);
        var note = new Label("WLAN-Zugangsdaten sind auf dem Tag auslesbar.");
        note.getStyleClass().add("muted");
        note.setWrapText(true);
        fields.getChildren().add(note);
        return () -> open.isSelected()
                ? new NDefWifi(ssid.getText(), WifiAuthentication.OPEN, WifiEncryption.NONE, "")
                : new NDefWifi(ssid.getText(), WifiAuthentication.WPA2_PERSONAL, WifiEncryption.AES,
                        password.getText());
    }

    private TextField textField(String label, String value, String prompt) {
        var input = new TextField(value);
        input.setPromptText(prompt);
        field(label, input);
        return input;
    }

    private void field(String label, Control input) {
        var caption = new Label(label);
        caption.setLabelFor(input);
        fields.getChildren().add(new VBox(4, caption, input));
        if (firstField == null) {
            firstField = input;
        }
    }
}
