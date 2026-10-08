package com.paulorchard.islandcraft.wormsofarrakis;

import java.util.Locale;

/** Reads optional positional command arguments. */
final class Args {

    private Args() {
    }

    static String text(String[] args, int index, String fallback) {
        return index < args.length ? args[index].toLowerCase(Locale.ROOT) : fallback;
    }

    static double number(String[] args, int index, double fallback) {
        if (index >= args.length) {
            return fallback;
        }
        try {
            return Double.parseDouble(args[index]);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
