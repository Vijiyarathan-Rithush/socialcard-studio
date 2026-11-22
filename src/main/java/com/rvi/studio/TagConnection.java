package com.rvi.studio;

import com.rvi.domain.NDefMessage;

import javax.smartcardio.CardException;

interface TagConnection extends AutoCloseable
{
    String readId() throws CardException;

    void write(NDefMessage message) throws CardException;

    @Override
    void close() throws CardException;
}
