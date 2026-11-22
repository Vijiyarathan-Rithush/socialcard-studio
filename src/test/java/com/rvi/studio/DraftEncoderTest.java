package com.rvi.studio;

import com.rvi.application.NDefDecoder;
import com.rvi.domain.NDefText;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class DraftEncoderTest
{
    @Test
    void countsTheNdefRecordAndItsTlvEnvelope()
    {
        final List<NDefText> records = List.of(new NDefText("Hi", "en"));
        final DraftEncoding draft = new DraftEncoder().encode(List.copyOf(records));

        assertEquals(12, draft.bytes());
        assertEquals(records, new NDefDecoder().decode(draft.message()));
    }
}
