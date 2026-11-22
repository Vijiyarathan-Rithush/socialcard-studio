package com.rvi.studio;

import com.rvi.application.exception.ReaderServiceException;
import com.rvi.domain.NDefMessage;

import javax.smartcardio.CardException;
import java.util.Objects;
import java.util.function.Function;

final class NfcTagAccess
{
    private final Function<String, TagConnection> openConnection;

    NfcTagAccess(final Function<String, TagConnection> openConnection)
    {
        this.openConnection = Objects.requireNonNull(openConnection);
    }

    String identify(final String readerName)
    {
        try (final TagConnection connection = openConnection.apply(readerName))
        {
            return requireTagId(connection.readId());
        }
        catch (final CardException error)
        {
            throw new ReaderServiceException("Could not identify the tag", error);
        }
    }

    void write(final String readerName, final String expectedTagId, final NDefMessage message)
    {
        requireTagId(expectedTagId);

        try (final TagConnection connection = openConnection.apply(readerName))
        {
            if (!expectedTagId.equals(requireTagId(connection.readId())))
            {
                throw new ReaderServiceException("The tag has changed");
            }

            connection.write(message);
        }
        catch (final CardException error)
        {
            throw new ReaderServiceException("Could not write to the tag", error);
        }
    }

    private static String requireTagId(final String tagId)
    {
        if (tagId == null || tagId.isBlank())
        {
            throw new ReaderServiceException("The reader returned an empty tag identifier");
        }

        return tagId;
    }
}
