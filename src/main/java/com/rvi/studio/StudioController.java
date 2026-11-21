package com.rvi.studio;

import com.rvi.application.IReaderListener;
import com.rvi.application.ReaderMonitor;
import com.rvi.application.TlvEncoder;
import com.rvi.application.creator.NDefMessageCreator;
import com.rvi.domain.*;
import com.rvi.service.ReaderService;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.concurrent.Task;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import javax.smartcardio.CardTerminal;
import javax.smartcardio.TerminalFactory;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class StudioController {
    private static final int REFERENCE_CAPACITY = 496;
    private static final PseudoClass OVER_CAPACITY = PseudoClass.getPseudoClass("over-capacity");
    private final ExecutorService worker = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().daemon().name("socialcard-io").factory());

    @FXML private ComboBox<RecordEditor.Kind> recordType;
    @FXML private ListView<NDefContent> records;
    @FXML private Button addButton;
    @FXML private Button editButton;
    @FXML private Button removeButton;
    @FXML private Label entryCount;
    @FXML private ComboBox<String> readers;
    @FXML private Label readerStatus;
    @FXML private Button refreshButton;
    @FXML private Label byteCount;
    @FXML private ProgressBar capacityBar;
    @FXML private Button readButton;
    @FXML private Button writeButton;
    @FXML private Label status;

    private ReaderMonitor monitor;
    private int readerGeneration;
    private int cardGeneration;
    private boolean busy;
    private boolean refreshingReaders;
    private boolean cardPresent;
    private boolean closed;
    private NDefMessage draft;

    @FXML
    private void initialize() {
        recordType.getItems().setAll(RecordEditor.Kind.values());
        recordType.getSelectionModel().selectFirst();
        records.setPlaceholder(new Label("Füge einen Eintrag hinzu oder lies einen NFC-Tag."));
        records.setCellFactory(list -> new RecordCell());
        records.getItems().addListener((ListChangeListener<NDefContent>) change -> updateDraft());
        records.getSelectionModel().selectedIndexProperty().addListener((o, old, value) -> updateButtons());
        readers.valueProperty().addListener((o, old, name) -> {
            if (!refreshingReaders) {
                watchReader(name);
            }
        });
        updateDraft();
    }

    @FXML
    void refreshReaders() {
        closeMonitor();
        String previous = readers.getValue();
        readerStatus.setText("Suche nach PC/SC-Lesern …");
        run("Leser werden gesucht …", "Lesersuche fehlgeschlagen", ReaderService::listReaders, names -> {
            refreshingReaders = true;
            readers.getItems().setAll(names);
            readers.setValue(previous != null && names.contains(previous)
                    ? previous : names.stream().findFirst().orElse(null));
            refreshingReaders = false;
            status.setText(names.isEmpty() ? "Schliesse einen PC/SC-NFC-Leser an." : "NFC-Leser verfügbar.");
            watchReader(readers.getValue());
        }, error -> {
            refreshingReaders = true;
            readers.getItems().clear();
            readers.setValue(null);
            refreshingReaders = false;
            readerStatus.setText("PC/SC-Lesersuche fehlgeschlagen.");
            updateButtons();
        });
    }

    private void watchReader(String name) {
        closeMonitor();
        if (name == null) {
            readerStatus.setText("Kein PC/SC-Leser gefunden.");
            return;
        }
        int generation = readerGeneration;
        readerStatus.setText("Warte auf einen NFC-Tag …");
        worker.execute(() -> {
            try {
                CardTerminal terminal = TerminalFactory.getDefault().terminals().getTerminal(name);
                if (terminal == null) {
                    throw new IllegalStateException("Der Leser ist nicht mehr verbunden.");
                }
                onCurrentReader(generation, () -> startMonitor(terminal, generation));
            } catch (Exception error) {
                onCurrentReader(generation, () -> monitorFailed(error));
            }
        });
    }

    private void startMonitor(CardTerminal terminal, int generation) {
        monitor = new ReaderMonitor(terminal, new IReaderListener() {
            @Override
            public void onCardInserted(CardTerminal reader) {
                onCurrentReader(generation, () -> setCardPresent(true));
            }

            @Override
            public void onCardRemoved(CardTerminal reader) {
                onCurrentReader(generation, () -> setCardPresent(false));
            }

            @Override
            public void onError(Exception error) {
                onCurrentReader(generation, () -> monitorFailed(error));
            }
        });
        monitor.start();
    }

    private void onCurrentReader(int generation, Runnable action) {
        Platform.runLater(() -> {
            if (!closed && generation == readerGeneration) {
                action.run();
            }
        });
    }

    private void setCardPresent(boolean present) {
        cardGeneration++;
        cardPresent = present;
        readerStatus.setText(present ? "NFC-Tag erkannt." : "Warte auf einen NFC-Tag …");
        updateButtons();
    }

    private void monitorFailed(Exception error) {
        closeMonitor();
        readerStatus.setText("Leser nicht verfügbar. Suche erneut nach Lesern.");
        status.setText(errorMessage("Leserfehler", error));
    }

    private void closeMonitor() {
        readerGeneration++;
        cardGeneration++;
        if (monitor != null) {
            monitor.close();
            monitor = null;
        }
        cardPresent = false;
        updateButtons();
    }

    @FXML
    private void addRecord() {
        RecordEditor.show(records.getScene().getWindow(), recordType.getValue(), null)
                .ifPresent(value -> {
                    records.getItems().add(value);
                    records.getSelectionModel().selectLast();
                });
    }

    @FXML
    private void editRecord() {
        int index = records.getSelectionModel().getSelectedIndex();
        if (index >= 0) {
            NDefContent current = records.getItems().get(index);
            RecordEditor.show(records.getScene().getWindow(), RecordEditor.kindOf(current), current)
                    .ifPresent(value -> records.getItems().set(index, value));
        }
    }

    @FXML
    private void removeRecord() {
        int index = records.getSelectionModel().getSelectedIndex();
        if (index >= 0) {
            records.getItems().remove(index);
        }
    }

    private void updateDraft() {
        int count = records.getItems().size();
        entryCount.setText(count == 1 ? "1 Eintrag" : count + " Einträge");
        draft = null;
        byteCount.getStyleClass().remove("error-text");
        capacityBar.pseudoClassStateChanged(OVER_CAPACITY, false);
        try {
            int bytes = 0;
            if (count > 0) {
                var creator = new NDefMessageCreator();
                records.getItems().forEach(creator::add);
                var message = creator.build();
                bytes = new TlvEncoder().encode(message).length;
                draft = message;
            }
            byteCount.setText(bytes + " / " + REFERENCE_CAPACITY + " Bytes");
            capacityBar.setProgress(Math.min(1, (double) bytes / REFERENCE_CAPACITY));
            capacityBar.pseudoClassStateChanged(OVER_CAPACITY, bytes > REFERENCE_CAPACITY);
            if (bytes > REFERENCE_CAPACITY) {
                byteCount.getStyleClass().add("error-text");
            }
        } catch (RuntimeException error) {
            byteCount.setText("Grösse nicht verfügbar");
            capacityBar.setProgress(0);
            status.setText(errorMessage("Entwurf konnte nicht codiert werden", error));
        }
        updateButtons();
    }

    @FXML
    private void readTag() {
        if (!records.getItems().isEmpty() && !confirm("Entwurf ersetzen?",
                "Beim Lesen werden die Einträge im Editor durch den Tag-Inhalt ersetzt.", "Tag lesen")) {
            return;
        }
        if (readButton.isDisabled()) {
            return;
        }
        String name = readers.getValue();
        run("Tag wird gelesen …", "Lesen fehlgeschlagen", () -> {
            try (var service = ReaderService.create(name)) {
                service.connect();
                return service.readContents();
            }
        }, values -> {
            records.getItems().setAll(values);
            status.setText("Tag gelesen. " + entryCount.getText() + " geladen.");
        }, error -> {});
    }

    @FXML
    private void writeTag() {
        var data = draft;
        int confirmedCard = cardGeneration;
        if (data == null || writeButton.isDisabled() || !confirm("Tag überschreiben?",
                "Der aktuelle Inhalt des Tags wird ersetzt. Lass den Tag bis zur Bestätigung auf dem Leser.",
                "Auf Tag schreiben")) {
            return;
        }
        if (confirmedCard != cardGeneration) {
            status.setText("Der Tag hat sich geändert. Starte den Schreibvorgang erneut.");
            return;
        }
        String name = readers.getValue();
        run("Tag wird beschrieben und geprüft …", "Schreiben fehlgeschlagen", () -> {
            try (var service = ReaderService.create(name)) {
                service.connect();
                service.write(data);
                return null;
            }
        }, ignored -> status.setText("Tag erfolgreich beschrieben und geprüft."), error -> {});
    }

    private boolean confirm(String title, String message, String action) {
        var proceed = new ButtonType(action, ButtonBar.ButtonData.OK_DONE);
        var dialog = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.CANCEL, proceed);
        dialog.initOwner(records.getScene().getWindow());
        dialog.setTitle("SocialCard Studio");
        dialog.setHeaderText(title);
        RecordEditor.style(dialog.getDialogPane());
        var cancel = (Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        ((Button) dialog.getDialogPane().lookupButton(proceed)).setDefaultButton(false);
        cancel.setDefaultButton(true);
        return dialog.showAndWait().filter(proceed::equals).isPresent();
    }

    private <T> void run(String progress, String errorTitle, Callable<T> work,
                         Consumer<T> success, Consumer<Throwable> failure) {
        busy = true;
        status.setText(progress);
        updateButtons();
        var task = new Task<T>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(event -> {
            busy = false;
            success.accept(task.getValue());
            updateButtons();
        });
        task.setOnFailed(event -> {
            busy = false;
            status.setText(errorMessage(errorTitle, task.getException()));
            failure.accept(task.getException());
            updateButtons();
        });
        worker.execute(task);
    }

    static String errorMessage(String title, Throwable error) {
        String detail = error.getMessage();
        return detail == null || detail.isBlank() ? title + "." : title + " · " + detail;
    }

    private void updateButtons() {
        boolean noSelection = records.getSelectionModel().isEmpty();
        addButton.setDisable(busy);
        editButton.setDisable(busy || noSelection);
        removeButton.setDisable(busy || noSelection);
        recordType.setDisable(busy);
        readers.setDisable(busy || readers.getItems().isEmpty());
        refreshButton.setDisable(busy);
        readButton.setDisable(busy || !cardPresent);
        writeButton.setDisable(busy || !cardPresent || draft == null);
    }

    boolean isBusy() {
        return busy;
    }

    void close() {
        closed = true;
        closeMonitor();
        worker.shutdownNow();
    }

    private static final class RecordCell extends ListCell<NDefContent> {
        private final Label type = new Label();
        private final Label detail = new Label();
        private final VBox row = new VBox(3, type, detail);

        private RecordCell() {
            type.getStyleClass().add("record-type");
            detail.getStyleClass().add("record-detail");
            detail.setMaxWidth(Double.MAX_VALUE);
            row.prefWidthProperty().bind(widthProperty().subtract(36));
        }

        @Override
        protected void updateItem(NDefContent item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setAccessibleText(null);
                return;
            }
            type.setText(RecordEditor.kindOf(item).toString());
            detail.setText(switch (item) {
                case NDefUri uri -> uri.uri();
                case NDefText text -> text.text().lines().findFirst().orElse("");
                case NDefContact contact -> (contact.firstName() + " " + contact.lastName()).strip();
                case NDefWifi wifi -> wifi.ssid();
            });
            setGraphic(row);
            setAccessibleText(type.getText() + ", " + detail.getText());
        }
    }
}
