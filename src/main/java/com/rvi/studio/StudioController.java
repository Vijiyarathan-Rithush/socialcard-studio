package com.rvi.studio;

import com.rvi.application.ReaderMonitor;
import com.rvi.domain.NDefContent;
import com.rvi.domain.NDefMessage;
import com.rvi.service.IReaderService;
import com.rvi.service.ReaderService;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;

import javax.smartcardio.CardTerminal;
import javax.smartcardio.TerminalFactory;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

public final class StudioController
{
    static final ButtonType CANCEL = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

    private static final int REFERENCE_CAPACITY = 496;
    private static final int MAX_DRAFT_RECORDS = 504;
    private static final PseudoClass OVER_CAPACITY = PseudoClass.getPseudoClass("over-capacity");

    private final ExecutorService worker = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().daemon().name("socialcard-io").factory());
    private final ExecutorService encodingWorker = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().daemon().name("socialcard-encoding").factory());
    private final DraftEncoder draftEncoder = new DraftEncoder();
    private final NfcTagAccess tagAccess = new NfcTagAccess(PcscTagConnection::open);

    @FXML private ComboBox<RecordKind> recordType;
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
    private List<NDefContent> savedRecords = List.of();
    private Future<?> encodingJob;
    private long draftRevision;
    private NDefMessage draft;

    @FXML
    private void initialize()
    {
        recordType.getItems().setAll(RecordKind.values());
        recordType.getSelectionModel().selectFirst();

        records.setPlaceholder(new Label("Add a record or read an NFC tag"));
        records.setCellFactory(list -> new RecordCell().view());
        records.getItems().addListener((ListChangeListener<NDefContent>) change -> updateDraft());
        records.getSelectionModel().selectedIndexProperty().addListener(
                (selection, previousIndex, selectedIndex) -> updateButtons());

        readers.valueProperty().addListener((selectedReader, previousName, name) ->
        {
            if (!refreshingReaders)
            {
                watchReader(name);
            }
        });

        updateDraft();
    }

    @FXML
    void refreshReaders()
    {
        final String previous = readers.getValue();

        closeMonitor();
        readerStatus.setText("Looking for PC/SC readers");

        run("Scanning for readers", "Reader discovery failed", ReaderService::listReaders, names ->
        {
            refreshingReaders = true;
            readers.getItems().setAll(names);

            if (previous != null && names.contains(previous))
            {
                readers.setValue(previous);
            }
            else
            {
                readers.setValue(names.stream().findFirst().orElse(null));
            }

            refreshingReaders = false;

            if (names.isEmpty())
            {
                status.setText("Connect a PC/SC NFC reader");
            }
            else
            {
                status.setText("NFC reader available");
            }

            watchReader(readers.getValue());
        }, error ->
        {
            refreshingReaders = true;
            readers.getItems().clear();
            readers.setValue(null);
            refreshingReaders = false;
            readerStatus.setText("PC/SC reader discovery failed");
        });
    }

    private void watchReader(final String name)
    {
        final int generation;

        closeMonitor();

        if (name == null)
        {
            readerStatus.setText("No PC/SC reader found");
            return;
        }

        generation = readerGeneration;
        readerStatus.setText("Waiting for an NFC tag");

        worker.execute(() ->
        {
            try
            {
                final CardTerminal terminal = TerminalFactory.getDefault().terminals().getTerminal(name);

                if (terminal == null)
                {
                    throw new IllegalStateException("The reader was disconnected");
                }

                onCurrentReader(generation, () -> startMonitor(terminal, generation));
            }
            catch (Exception error)
            {
                onCurrentReader(generation, () -> monitorFailed(error));
            }
        });
    }

    private void startMonitor(final CardTerminal terminal, final int generation)
    {
        final ReaderStatusListener listener = new ReaderStatusListener(
                present -> onCurrentReader(generation, () -> setCardPresent(present)),
                error -> onCurrentReader(generation, () -> monitorFailed(error)));

        monitor = new ReaderMonitor(terminal, listener);
        monitor.start();
    }

    private void onCurrentReader(final int generation, final Runnable action)
    {
        Platform.runLater(() ->
        {
            if (!closed && generation == readerGeneration)
            {
                action.run();
            }
        });
    }

    private void setCardPresent(final boolean present)
    {
        cardGeneration++;
        cardPresent = present;

        if (present)
        {
            readerStatus.setText("NFC tag detected");
        }
        else
        {
            readerStatus.setText("Waiting for an NFC tag");
        }

        updateButtons();
    }

    private void monitorFailed(final Exception error)
    {
        closeMonitor();
        readerStatus.setText("Scan again to reconnect the reader");
        status.setText(errorMessage("Reader error", error));
    }

    private void closeMonitor()
    {
        readerGeneration++;
        cardGeneration++;

        if (monitor != null)
        {
            monitor.close();
            monitor = null;
        }

        cardPresent = false;
        updateButtons();
    }

    @FXML
    private void addRecord()
    {
        RecordEditor.show(records.getScene().getWindow(), recordType.getValue(), null)
                .ifPresent(value ->
                {
                    records.getItems().add(value);
                    records.getSelectionModel().selectLast();
                });
    }

    @FXML
    private void editRecord()
    {
        final int index = records.getSelectionModel().getSelectedIndex();
        final NDefContent current;

        if (index < 0)
        {
            return;
        }

        current = records.getItems().get(index);
        RecordEditor.show(records.getScene().getWindow(), RecordKind.fromContent(current), current)
                .ifPresent(value -> records.getItems().set(index, value));
    }

    @FXML
    private void removeRecord()
    {
        final int index = records.getSelectionModel().getSelectedIndex();

        if (index >= 0)
        {
            records.getItems().remove(index);
        }
    }

    private void updateDraft()
    {
        final List<NDefContent> snapshot = List.copyOf(records.getItems());
        final int count = snapshot.size();
        final long revision = ++draftRevision;

        if (encodingJob != null)
        {
            encodingJob.cancel(false);
            encodingJob = null;
        }

        if (count == 1)
        {
            entryCount.setText("1 record");
        }
        else
        {
            entryCount.setText(count + " records");
        }

        draft = null;
        byteCount.getStyleClass().remove("error-text");
        capacityBar.pseudoClassStateChanged(OVER_CAPACITY, false);
        capacityBar.setProgress(0);

        if (snapshot.isEmpty())
        {
            byteCount.setText("0 / " + REFERENCE_CAPACITY + " Bytes");
            updateButtons();
            return;
        }

        byteCount.setText("Calculating size");
        updateButtons();
        encodingJob = encodingWorker.submit(() -> encodeDraft(snapshot, revision));
    }

    private void encodeDraft(final List<NDefContent> snapshot, final long revision)
    {
        final DraftEncoding encoded;

        try
        {
            encoded = draftEncoder.encode(snapshot);
        }
        catch (final RuntimeException error)
        {
            Platform.runLater(() ->
            {
                if (closed || revision != draftRevision)
                {
                    return;
                }

                encodingJob = null;
                byteCount.setText("Size unavailable");
                status.setText(errorMessage("Could not encode the draft", error));
            });
            return;
        }

        Platform.runLater(() -> showDraftEncoding(encoded, revision));
    }

    private void showDraftEncoding(final DraftEncoding encoded, final long revision)
    {
        final int bytes = encoded.bytes();

        if (closed || revision != draftRevision)
        {
            return;
        }

        encodingJob = null;
        final boolean overCapacity = bytes > REFERENCE_CAPACITY;
        draft = overCapacity ? null : encoded.message();
        byteCount.setText(bytes + " / " + REFERENCE_CAPACITY + " Bytes");
        capacityBar.setProgress(Math.min(1, (double) bytes / REFERENCE_CAPACITY));
        capacityBar.pseudoClassStateChanged(OVER_CAPACITY, overCapacity);

        if (overCapacity)
        {
            byteCount.getStyleClass().add("error-text");
        }

        updateButtons();
    }

    @FXML
    private void readTag()
    {
        final String readerName;

        if (!records.getItems().isEmpty() && !confirm("Replace draft",
                "Reading the tag will replace the records in the editor", "Read tag"))
        {
            return;
        }

        if (readButton.isDisabled())
        {
            return;
        }

        readerName = readers.getValue();

        run("Reading tag", "Read failed", () ->
        {
            try (final IReaderService service = ReaderService.create(readerName))
            {
                service.connect();
                return service.readContents();
            }
        }, values ->
        {
            savedRecords = List.copyOf(values);
            records.getItems().setAll(savedRecords);
            status.setText("Tag records loaded into the editor");
        }, error -> {});
    }

    @FXML
    private void writeTag()
    {
        final NDefMessage data = draft;
        final int confirmedCard = cardGeneration;
        final String readerName = readers.getValue();
        final List<NDefContent> snapshot = List.copyOf(records.getItems());

        if (data == null || writeButton.isDisabled())
        {
            return;
        }

        run("Identifying tag", "Could not identify the tag", () -> tagAccess.identify(readerName),
                tagId -> confirmWrite(readerName, tagId, confirmedCard, snapshot, data), error -> {});
    }

    private void confirmWrite(final String readerName, final String tagId, final int confirmedCard,
                              final List<NDefContent> snapshot, final NDefMessage data)
    {
        if (!cardPresent || confirmedCard != cardGeneration)
        {
            status.setText("Start the write again to use the new tag");
            return;
        }

        status.setText("Tag ready to write");

        if (!confirm("Overwrite tag",
                "Keep the tag on the reader while its contents are replaced and verified",
                "Write to tag"))
        {
            return;
        }

        if (confirmedCard != cardGeneration)
        {
            status.setText("Start the write again to use the new tag");
            return;
        }

        run("Writing and verifying tag", "Write failed", () ->
        {
            tagAccess.write(readerName, tagId, data);
            return null;
        }, ignored ->
        {
            savedRecords = snapshot;
            status.setText("Tag written and verified");
        }, error -> {});
    }

    private boolean confirm(final String title, final String message, final String action)
    {
        final ButtonType proceed = new ButtonType(action, ButtonBar.ButtonData.OK_DONE);
        final Alert dialog = new Alert(Alert.AlertType.CONFIRMATION, message, CANCEL, proceed);
        final Button cancel = (Button) dialog.getDialogPane().lookupButton(CANCEL);
        final Button confirm = (Button) dialog.getDialogPane().lookupButton(proceed);

        dialog.initOwner(records.getScene().getWindow());
        dialog.setTitle("SocialCard Studio");
        dialog.setHeaderText(title);
        RecordEditor.style(dialog.getDialogPane());

        confirm.setDefaultButton(false);
        cancel.setDefaultButton(true);

        return dialog.showAndWait().filter(proceed::equals).isPresent();
    }

    private <T> void run(final String progress, final String errorTitle, final Callable<T> work,
                         final Consumer<T> success, final Consumer<Throwable> failure)
    {
        busy = true;
        status.setText(progress);
        updateButtons();

        worker.execute(() ->
        {
            final T result;

            try
            {
                result = work.call();
            }
            catch (final Throwable error)
            {
                Platform.runLater(() ->
                {
                    if (closed)
                    {
                        return;
                    }

                    busy = false;
                    status.setText(errorMessage(errorTitle, error));
                    try
                    {
                        failure.accept(error);
                    }
                    finally
                    {
                        updateButtons();
                    }
                });
                return;
            }

            Platform.runLater(() ->
            {
                if (closed)
                {
                    return;
                }

                busy = false;
                try
                {
                    success.accept(result);
                }
                finally
                {
                    updateButtons();
                }
            });
        });
    }

    static String errorMessage(final String title, final Throwable error)
    {
        final String detail = error.getMessage();

        if (detail == null || detail.isBlank())
        {
            return title;
        }

        return title + "\n" + detail;
    }

    private void updateButtons()
    {
        final boolean noSelection = records.getSelectionModel().isEmpty();

        addButton.setDisable(busy || records.getItems().size() >= MAX_DRAFT_RECORDS);
        editButton.setDisable(busy || noSelection);
        removeButton.setDisable(busy || noSelection);
        recordType.setDisable(busy);
        readers.setDisable(busy || readers.getItems().isEmpty());
        refreshButton.setDisable(busy);
        readButton.setDisable(busy || !cardPresent);
        writeButton.setDisable(busy || !cardPresent || draft == null);
    }

    boolean isBusy()
    {
        return busy;
    }

    boolean confirmDiscardDraft()
    {
        if (records.getItems().equals(savedRecords))
        {
            return true;
        }

        return confirm("Discard draft", "Changes to the draft will be lost", "Discard");
    }

    void close()
    {
        closed = true;
        closeMonitor();
        encodingWorker.shutdownNow();
        worker.shutdownNow();
    }
}
