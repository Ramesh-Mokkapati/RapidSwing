package com.rapidswing.generator;

import javax.swing.SwingUtilities;

/**
 * Generates RapidSwing applications. Without arguments it opens the wizard; with arguments it runs on the
 * command line (see {@code --help}).
 */
public final class Generator {

    private Generator() {
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            SwingUtilities.invokeLater(GeneratorWizard::open);
            return;
        }
        System.exit(GeneratorCli.run(args, System.out, System.err));
    }
}
