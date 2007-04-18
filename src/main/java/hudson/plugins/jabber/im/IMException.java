package hudson.plugins.jabber.im;

import java.io.IOException;

/**
 * Reprsents any kind of protocol-level error that may occur.
 * @author Uwe Schaefer
 * @TODO this is obviously not fine-grained enough.
 */
public class IMException extends IOException
{
    private static final long serialVersionUID = 1L;

    public IMException(final Exception e)
    {
        super(e.getMessage());
    }

}
