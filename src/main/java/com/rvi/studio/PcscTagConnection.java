package com.rvi.studio;

import com.rvi.application.ReaderRepository;
import com.rvi.application.exception.ReaderServiceException;
import com.rvi.domain.NDefMessage;

import javax.smartcardio.Card;
import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import javax.smartcardio.TerminalFactory;
import java.util.HexFormat;

final class PcscTagConnection implements TagConnection
{
    private static final CommandAPDU GET_UID = new CommandAPDU(0xFF, 0xCA, 0, 0, 256);
    private static final int SUCCESS_STATUS = 0x9000;
    private static final int NTAG215_UID_BYTES = 7;

    private final Card card;

    private PcscTagConnection(final Card card)
    {
        this.card = card;
    }

    static TagConnection open(final String readerName)
    {
        final CardTerminal terminal = TerminalFactory.getDefault().terminals().getTerminal(readerName);

        if (terminal == null)
        {
            throw new ReaderServiceException("The reader is no longer connected");
        }

        try
        {
            return new PcscTagConnection(terminal.connect("*"));
        }
        catch (final CardException error)
        {
            throw new ReaderServiceException("Could not connect to the tag", error);
        }
    }

    @Override
    public String readId() throws CardException
    {
        final ResponseAPDU response = card.getBasicChannel().transmit(GET_UID);

        if (response.getSW() != SUCCESS_STATUS || response.getNr() != NTAG215_UID_BYTES)
        {
            throw new ReaderServiceException("The reader could not identify the NTAG215 tag");
        }

        return HexFormat.of().formatHex(response.getData());
    }

    @Override
    public void write(final NDefMessage message) throws CardException
    {
        new ReaderRepository(card.getBasicChannel()).write(message);
    }

    @Override
    public void close() throws CardException
    {
        card.disconnect(false);
    }
}
