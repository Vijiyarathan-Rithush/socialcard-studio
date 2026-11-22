package com.rvi.studio;

import com.rvi.application.NDefEncoder;
import com.rvi.application.exception.ReaderServiceException;
import com.rvi.domain.NDefMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class NfcTagAccessTest
{
    private static final String READER = "Test reader";
    private static final String ORIGINAL_TAG = "04010203040506";
    private static final String DIFFERENT_TAG = "04111213141516";
    private static final NDefMessage DRAFT = new NDefEncoder().encodeURI("https://example.com");

    @Test
    void checksTheTagOnTheConnectionThatWrites()
    {
        final RecordingTagConnection identification = new RecordingTagConnection(ORIGINAL_TAG);
        final RecordingTagConnection writing = new RecordingTagConnection(ORIGINAL_TAG);
        final ArrayDeque<RecordingTagConnection> connections = new ArrayDeque<>(List.of(identification, writing));
        final NfcTagAccess tags = new NfcTagAccess(reader ->
        {
            assertEquals(READER, reader);
            return connections.removeFirst();
        });
        final String confirmedId = tags.identify(READER);

        tags.write(READER, confirmedId, DRAFT);

        assertEquals(List.of("readId", "close"), identification.calls());
        assertEquals(List.of("readId", "write", "close"), writing.calls());
    }

    @Test
    void neverWritesToAReplacementTag()
    {
        final RecordingTagConnection identification = new RecordingTagConnection(ORIGINAL_TAG);
        final RecordingTagConnection replacement = new RecordingTagConnection(DIFFERENT_TAG);
        final ArrayDeque<RecordingTagConnection> connections = new ArrayDeque<>(List.of(identification, replacement));
        final NfcTagAccess tags = new NfcTagAccess(reader -> connections.removeFirst());
        final String confirmedId = tags.identify(READER);

        assertThrows(ReaderServiceException.class, () -> tags.write(READER, confirmedId, DRAFT));
        assertEquals(List.of("readId", "close"), replacement.calls());
    }

    @Test
    void neverWritesWhenTheIdentityIsUnavailable()
    {
        final RecordingTagConnection unidentified = new RecordingTagConnection("");
        final NfcTagAccess tags = new NfcTagAccess(reader -> unidentified);

        assertThrows(ReaderServiceException.class, () -> tags.write(READER, ORIGINAL_TAG, DRAFT));
        assertEquals(List.of("readId", "close"), unidentified.calls());
    }
}
