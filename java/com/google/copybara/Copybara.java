// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Entry point for Copybara library.
 */
public class Copybara {
    private static final Logger logger = Logger.getLogger(Copybara.class.getName());

    public void doYourMagic() {
        logger.log(Level.INFO, "Best transformer is the identity transformer!");
    }
}
