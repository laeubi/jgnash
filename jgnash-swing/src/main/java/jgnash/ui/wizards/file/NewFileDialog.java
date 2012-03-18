/*
 * jGnash, a personal finance application
 * Copyright (C) 2001-2012 Craig Cavanaugh
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package jgnash.ui.wizards.file;

import java.awt.Frame;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import javax.swing.SwingWorker;

import jgnash.engine.Account;
import jgnash.engine.AccountTreeXMLFactory;
import jgnash.engine.AccountType;
import jgnash.engine.CurrencyNode;
import jgnash.engine.DataStoreType;
import jgnash.engine.DefaultCurrencies;
import jgnash.engine.Engine;
import jgnash.engine.EngineFactory;
import jgnash.engine.RootAccount;
import jgnash.ui.UIApplication;
import jgnash.ui.components.wizard.WizardDialog;
import jgnash.util.Resource;

/**
 * Dialog for creating a new file
 *
 * @author Craig Cavanaugh
 *
 */
public class NewFileDialog extends WizardDialog {

    public enum Settings {

        BACKEND,
        CURRENCIES,
        DEFAULT_CURRENCIES,
        DEFAULT_CURRENCY,
        DATABASENAME,
        ACCOUNTSET,
        TYPE
    }

    private NewFileDialog(Frame parent) {
        super(parent);

        setTitle(rb.getString("Title.NewFile"));
    }

    public static void showDialog(final Frame parent) {

        final class Setup extends SwingWorker<Void, Void> {

            NewFileDialog d;

            public Setup(NewFileDialog dialog) {
                d = dialog;
            }

            @SuppressWarnings("unchecked")
            @Override
            protected Void doInBackground() throws Exception {
                Resource rb = Resource.get();

                UIApplication.getFrame().displayWaitMessage(rb.getString("Message.PleaseWait"));

                String database = (String) d.getSetting(Settings.DATABASENAME);

                Set<CurrencyNode> nodes = (Set<CurrencyNode>) d.getSetting(Settings.CURRENCIES);
                CurrencyNode defaultCurrency = (CurrencyNode) d.getSetting(Settings.DEFAULT_CURRENCY);
                DataStoreType type = (DataStoreType) d.getSetting(Settings.TYPE);

                // have to close the engine first
                EngineFactory.closeEngine(EngineFactory.DEFAULT);

                // try to delete any existing database
                EngineFactory.deleteDatabase(database);

                Engine e = EngineFactory.bootLocalEngine(database, EngineFactory.DEFAULT, type);

                // match the existing default to the new default.  If they match, replace to prevent
                // creation of a duplicate currency
                if (e.getDefaultCurrency().matches(defaultCurrency)) {
                    defaultCurrency = e.getDefaultCurrency();
                }

                // make sure a duplicate default is not added
                for (CurrencyNode node : nodes) {
                    if (!node.matches(defaultCurrency)) {
                        e.addCommodity(node);
                    }
                }

                if (!defaultCurrency.equals(e.getDefaultCurrency())) {
                    e.setDefaultCurrency(defaultCurrency);
                }

                List<RootAccount> accountList = (List<RootAccount>) d.getSetting(Settings.ACCOUNTSET);

                if (!accountList.isEmpty()) { // import account sets
                    for (RootAccount root : accountList) {
                        AccountTreeXMLFactory.importAccountTree(e, root);
                    }
                } else { // none selected, create a very basic account set
                    RootAccount root = e.getRootAccount();

                    Account bank = new Account(AccountType.BANK, defaultCurrency);
                    bank.setDescription(rb.getString("Name.BankAccounts"));
                    bank.setName(rb.getString("Name.BankAccounts"));

                    e.addAccount(root, bank);

                    Account income = new Account(AccountType.INCOME, defaultCurrency);
                    income.setDescription(rb.getString("Name.IncomeAccounts"));
                    income.setName(rb.getString("Name.IncomeAccounts"));

                    e.addAccount(root, income);

                    Account expense = new Account(AccountType.EXPENSE, defaultCurrency);
                    expense.setDescription(rb.getString("Name.ExpenseAccounts"));
                    expense.setName(rb.getString("Name.ExpenseAccounts"));

                    e.addAccount(root, expense);
                }

                return null;
            }

            @Override
            protected void done() {
                UIApplication.getFrame().stopWaitMessage();
            }
        }

        class DisplayDialog extends SwingWorker<Set<CurrencyNode>, Object> {

            @Override
            public Set<CurrencyNode> doInBackground() {
                return DefaultCurrencies.generateCurrencies();
            }

            @Override
            protected void done() {
                try {
                    NewFileDialog d = new NewFileDialog(parent);

                    d.setSetting(NewFileDialog.Settings.DEFAULT_CURRENCIES, get());
                    d.setSetting(NewFileDialog.Settings.DATABASENAME, EngineFactory.getDefaultDatabase());

                    d.addTaskPage(new NewFileOne());
                    d.addTaskPage(new NewFileTwo());
                    d.addTaskPage(new NewFileThree());
                    d.addTaskPage(new NewFileFour());
                    d.addTaskPage(new NewFileSummary());

                    d.setLocationRelativeTo(parent);
                    d.setVisible(true);

                    if (d.isWizardValid()) {
                        new Setup(d).execute();
                    }
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }
            }
        }

        new DisplayDialog().execute();
    }

}
