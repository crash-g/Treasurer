package com.muciaccia.plugins;

import com.muciaccia.plugins.Bot;
import com.muciaccia.plugins.Message;
import com.muciaccia.plugins.Plugin;
import com.muciaccia.plugins.backend.Balance;
import com.muciaccia.plugins.backend.pojo.BalanceStatement;
import com.muciaccia.plugins.backend.Expense;
import com.muciaccia.plugins.backend.pojo.User;
import com.muciaccia.plugins.exceptions.ExpenseNotFinalizedException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class is an implementation of a treasurer plugin for a generic bot. The plugin registers the expenses of a group
 * of users and provides an optimized way of giving money back with the minimum possible number of transactions.
 *
 * Allowed messages include: BALANCE (shows a summary of debts), HISTORY (shows a summary of user debts and credits),
 * messages to add expenses and messages to manage groups of users.
 *
 * In particular, messages to add expenses should have the form <AMOUNT>|<HANDLE>[,<HANDLE>]*[ "<MESSAGE>"], where HANDLE
 * is a user name or a group name with optional modifiers (+ and *) to allow uneven splitting.
 */
public class Treasurer implements Plugin {
    // global settings for numbers
    public static final int NORMAL_SCALE = 2;
    public static final RoundingMode NORMAL_ROUNDING_MODE = RoundingMode.HALF_EVEN;
    public static final BigDecimal ZERO = BigDecimal.valueOf(0).setScale(NORMAL_SCALE, NORMAL_ROUNDING_MODE);

    // an implementation of the bot
    private final Bot bot;

    // keys for stored objects
    private static final String EXPENSES_KEY = "EXPENSES_LIST";
    private static final String BALANCE_KEY = "BALANCE_SUMMARY";
    private static final String GROUPS_KEY = "GROUP_SET";

    // output strings
    private static final String LINE_END = "\n";
    private static final String DONE = "Done";
    private static final String HISTORY_DEBT = "%s%s - you pay back %s";
    private static final String HISTORY_CREDIT = "%s%s - you get back %s";
    private static final String BALANCE_STRING = "%s owes %s %s";
    private static final SimpleDateFormat SDF = new SimpleDateFormat("dd/MM/yyyy");

    // input keywords strings
    private static final String HISTORY = "HISTORY";
    private static final String BALANCE = "BALANCE";
    private static final String CREATE = "CREATE";
    private static final String ADD = "ADD";
    private static final String DELETE = "DELETE";

    // regexp group names
    private static final String AMOUNT = "amount";
    private static final String PARTICIPANTS = "participants";
    private static final String DESCRIPTION = "description";
    private static final String USER = "user";
    private static final String GROUP = "group";
    private static final String PLUS_MOD = "plusMod";
    private static final String STAR_MOD = "starMod";

    // REGEXP
    private static final String AMOUNT_REGEXP = "[0-9]+(?:\\.[0-9]{1,2})?";
    private static final String PLUS_REGEXP = "\\+[0-9]+(?:\\.[0-9]{1,2})?";
    private static final String STAR_REGEXP = "\\*[0-9]+(?:\\.[0-9])?";
    private static final String MODIFIER_REGEXP = "(?:" + PLUS_REGEXP + ")(?:" + STAR_REGEXP + ")?|(?:" + STAR_REGEXP
            + ")(?:" + PLUS_REGEXP + ")?";
    private static final String USER_REGEXP = "[A-Z]{2}";
    private static final String GROUP_REGEXP = "[A-Z]{3,12}";
    private static final String HANDLE_REGEXP = "(?:" + GROUP_REGEXP + "|" + USER_REGEXP + ")(?:" + MODIFIER_REGEXP +
            ")?";
    private static final String DESCRIPTION_REGEXP = "(?:\"\"|\"(?<" + DESCRIPTION + ">(?:\\\\\"|[^\"])*+)\")";

    // PATTERNS
    // matches and captures the plus modifier
    private static final Pattern PLUS_PATTERN = Pattern.compile("(?<" + PLUS_MOD + ">" + PLUS_REGEXP + ")");
    // matches and captures the star modifier
    private static final Pattern STAR_PATTERN = Pattern.compile("(?<" + STAR_MOD + ">" + STAR_REGEXP + ")");
    // matches a user name with an optional modifier (following a previous match and surrounded by space or comma) and captures the user name
    private static final Pattern USER_PATTERN = Pattern.compile("(?:\\G| |,)(?<" + USER + ">" + USER_REGEXP + ")(?:" + MODIFIER_REGEXP + ")?(?:$| |,)");
    // matches a group name with an optional modifier (following a previous match and surrounded by space or comma) and captures the group name
    private static final Pattern GROUP_PATTERN = Pattern.compile("(?:\\G| |,)(?<" + GROUP + ">" + GROUP_REGEXP  + ")(?:" + MODIFIER_REGEXP + ")?(?:$| |,)");
    // matches an expense string and captures amount, participants and description
    private static final Pattern EXPENSE_PATTERN = Pattern.compile("(?<" + AMOUNT + ">" + AMOUNT_REGEXP + ")\\|(?<" +
            PARTICIPANTS + ">" + HANDLE_REGEXP + "(?:," + HANDLE_REGEXP + ")*)" + "(?: " + DESCRIPTION_REGEXP + ")?");
    // matches a "create group" string and captures the group name
    private static final Pattern GROUP_CREATE_PATTERN = Pattern.compile(CREATE + " (?<" + GROUP + ">" + GROUP_REGEXP + ")");
    // matches a "add member to group" string and captures user name and group name
    private static final Pattern GROUP_ADD_PATTERN = Pattern.compile(ADD + " (?<" + USER + ">" + USER_REGEXP
            + ") (?<" + GROUP + ">" + GROUP_REGEXP + ")");
    // matches a "remove member from group" string and captures user name and group name
    private static final Pattern GROUP_DELETE_PATTERN = Pattern.compile(DELETE + " (?<" + USER + ">" + USER_REGEXP
            + ") (?<" + GROUP + ">" + GROUP_REGEXP + ")");

    // support variable that holds an expense which is being processed
    private Expense lastExpense;

    public Treasurer(Bot bot) {
        this.bot = bot;
    }

    @Override
    public void pluginHasBeenLoaded() {
        // initialize storage if needed
        if(null == bot.retrieveValue(EXPENSES_KEY)) {
            bot.storeValue(EXPENSES_KEY, new ArrayList<Expense>());
        }
        if(null == bot.retrieveValue(BALANCE_KEY)) {
            bot.storeValue(BALANCE_KEY, new Balance());
        }
        if(null == bot.retrieveValue(GROUPS_KEY)) {
            bot.storeValue(GROUPS_KEY, new HashMap<String,Set<User>>());
        }
    }

    @Override
    public void handleMessage(Message message) {
        String messageText = message.getText().trim();
        Matcher expense = EXPENSE_PATTERN.matcher(messageText);
        if (expense.matches()) {
            boolean handled = handleExpense(message, expense.group(AMOUNT), expense.group(PARTICIPANTS), expense
                    .group(DESCRIPTION));
            if (handled) {
                bot.sendText(DONE);
            }
        } else if (messageText.equals(HISTORY)) {
            printUserHistory(new User(message.getSender()));
        } else if (messageText.equals(BALANCE)) {
            printBalance();
        } else {
            Matcher createGroup = GROUP_CREATE_PATTERN.matcher(messageText);
            Matcher addToGroup = GROUP_ADD_PATTERN.matcher(messageText);
            Matcher removeFromGroup = GROUP_DELETE_PATTERN.matcher(messageText);
            if (createGroup.matches()) {
                if (handleCreateGroup(createGroup.group(GROUP))) {
                    bot.sendText(DONE);
                }
            } else if (addToGroup.matches()) {
                if (handleAddMemberToGroup(addToGroup.group(GROUP),addToGroup.group(USER))) {
                    bot.sendText(DONE);
                }
            } else if (removeFromGroup.matches()) {
                if (handleRemoveMemberFromGroup(removeFromGroup.group(GROUP),removeFromGroup.group(USER))) {
                    bot.sendText(DONE);
                }
            }
        }
    }

    /****** METHODS FOR INPUT HANDLING ******/
    // NOTE: input format must already be valid when calling these methods

    /**
     * Parses an expense and insert it into the system.
     * @param message the original message object, used to retrieve date and sender
     * @param amountString the parsed amount of the expense (ready to be converted to a number)
     * @param participants a string with all participants to the expense (in the form of both user and group names)
     * @param description the description of the expense
     * @return true if the expense is successfully inserted in the system, false otherwise
     */
    private boolean handleExpense(final Message message, final String amountString, final String participants, String
            description) {
        // format amount and description, then initialize a new Expense object
        BigDecimal amount = new BigDecimal(amountString);
        if (null != description) {
            description = description.replaceAll("\\\\\"", "\"");
        }
        addExpense(message.getDate(), description, amount, new User(message.getSender()));

        // parse user names from the participants, adding them to the expense (and aborting in case of duplicates)
        Set<User> participantSet = new HashSet<>();
        Matcher participantMatch = USER_PATTERN.matcher(participants);
        while (participantMatch.find()) {
            User user = new User(participantMatch.group(USER));
            if (participantSet.contains(user)) {
                return false;
            } else {
                String match = participantMatch.group();
                addParticipantToLastExpense(user,parsePlusMod(match),parseStarMod(match));
                participantSet.add(user);
            }
        }
        // parse group names from the participants, adding group members to the expense (and aborting in case of duplicates)
        Matcher groupMatch = GROUP_PATTERN.matcher(participants);
        while (groupMatch.find()) {
            List<User> groupMembers = getGroupMembers(groupMatch.group(GROUP));
            BigDecimal plusMod = parsePlusMod(groupMatch.group());
            BigDecimal starMod = parseStarMod(groupMatch.group());
            for (User user : groupMembers) {
                if (participantSet.contains(user)) {
                    return false;
                } else {
                    addParticipantToLastExpense(user,plusMod,starMod);
                    participantSet.add(user);
                }
            }
        }
        // try to finalize the expense
        try {
            finalizeExpense();
        } catch(ExpenseNotFinalizedException e) {
            return false;
        }
        return true;
    }

    /**
     * Parses a plus modifier in a string, returning the first match.
     * @param participant the string to parse
     * @return a BigDecimal equal to the plus modifier, or null if no match was found
     */
    private BigDecimal parsePlusMod(final String participant) {
        Matcher plusMatch = PLUS_PATTERN.matcher(participant);
        return plusMatch.find() ? new BigDecimal(plusMatch.group(PLUS_MOD).substring(1)) : null;
    }

    /**
     * Parses a star modifier in a string, returning the first match.
     * @param participant the string to parse
     * @return a BigDecimal equal to the star modifier, or null if no match was found
     */
    private BigDecimal parseStarMod(final String participant) {
        Matcher starMatch = STAR_PATTERN.matcher(participant);
        return starMatch.find() ? new BigDecimal(starMatch.group(STAR_MOD).substring(1)) : null;
    }

    /**
     * Inserts a new group in the system.
     * @param groupName the name of the group
     * @return true if the insert was successful
     */
    private boolean handleCreateGroup(final String groupName) {
        Map<String,Set<User>> groups = (Map<String,Set<User>>)bot.retrieveValue(GROUPS_KEY);
        if (!groups.containsKey(groupName)) {
            groups.put(groupName,new HashSet<>());
            return true;
        }
        return false;
    }

    /**
     * Adds a member to an existing group.
     * @param groupName the group name
     * @param groupMember the user to add
     * @return true if the operation was successful
     */
    private boolean handleAddMemberToGroup(final String groupName, final String groupMember) {
        Map<String,Set<User>> groups = (Map<String,Set<User>>)bot.retrieveValue(GROUPS_KEY);
        User newMember = new User(groupMember);
        if(groups.containsKey(groupName)) {
            Set<User> group = groups.get(groupName);
            if(!group.contains(newMember)) {
                groups.get(groupName).add(newMember);
                return true;
            }
        }
        return false;
    }

    /**
     * Removes a member from an existing group.
     * @param groupName the group name
     * @param groupMember the user to remove
     * @return true if the operation was successful
     */
    private boolean handleRemoveMemberFromGroup(final String groupName, final String groupMember) {
        Map<String,Set<User>> groups = (Map<String,Set<User>>)bot.retrieveValue(GROUPS_KEY);
        User oldMember = new User(groupMember);
        if(groups.containsKey(groupName)) {
            Set<User> group = groups.get(groupName);
            if (group.contains(oldMember)) {
                group.remove(oldMember);
                return true;
            }
        }
        return false;
    }

    /**
     * Returns a list of all the members of an existing group.
     * @param groupName the group name
     * @return a list with all the users in the group, or an empty list if the group does not exist
     */
    private List<User> getGroupMembers(final String groupName) {
        Map<String,Set<User>> groups = (Map<String,Set<User>>)bot.retrieveValue(GROUPS_KEY);
        return groups.containsKey(groupName) ? new ArrayList<>(groups.get(groupName)) : new ArrayList<>();
    }

    /****** BACK-END METHODS FOR EXPENSES ******/
    // NOTE: input for these methods must be correct, but may need formatting

    /**
     * Creates a new expense object.
     * @param date the date of the expense
     * @param description the description of the expense
     * @param amount the amount of the expense
     * @param payer the payer of the expense
     */
    private void addExpense(final Date date, final String description, BigDecimal amount, final User payer) {
        amount = amount.setScale(NORMAL_SCALE, NORMAL_ROUNDING_MODE);
        // save it in lastExpense for easier access
        lastExpense = new Expense(date, description, amount, payer);
    }

    /**
     * Adds a participant to the expense which is being processed, that is, the one in lastExpense
     * @param user a participant to the expense
     * @param plusMod the plus modifier of the participant (may be NULL)
     * @param starMod the star modifier of the participant (may be NULL)
     */
    private void addParticipantToLastExpense(final User user, BigDecimal plusMod, BigDecimal starMod) {
        if(null == plusMod) {
            plusMod = BigDecimal.valueOf(0);
        }
        plusMod = plusMod.setScale(NORMAL_SCALE, NORMAL_ROUNDING_MODE);
        if(null == starMod) {
            starMod = BigDecimal.valueOf(1);
        }
        starMod = starMod.setScale(NORMAL_SCALE, NORMAL_ROUNDING_MODE);
        lastExpense.addParticipant(user, plusMod, starMod);
    }

    /**
     * Finalizes an expense and puts it in the system.
     */
    private void finalizeExpense() throws ExpenseNotFinalizedException {
        lastExpense.finalizeExpense((Balance) bot.retrieveValue(BALANCE_KEY));
        List<Expense> expenses = (List<Expense>) bot.retrieveValue(EXPENSES_KEY);
        expenses.add(lastExpense);
    }


    /****** PRINTING METHODS ******/

    /**
     * Prints a user history.
     * @param user the user to process
     */
    private void printUserHistory(final User user) {
        StringBuilder sb = new StringBuilder();
        List<Expense> expenses = (List<Expense>) bot.retrieveValue(EXPENSES_KEY);
        for (Expense expense : expenses) {
            String description = (null == expense.getDescription()) ? "" : " " + expense.getDescription();
            if (expense.isPayer(user)) {
                sb.append(String.format(HISTORY_CREDIT, SDF.format(expense.getDate()), description,
                        expense.getPayerCredit())).append(LINE_END);
            } else if (expense.isDebtor(user)) {
                sb.append(String.format(HISTORY_DEBT, SDF.format(expense.getDate()), description,
                        expense.getDebtorDebt(user))).append(LINE_END);
            }
        }
        bot.sendText("".equals(sb.toString()) ? "" : sb.toString().substring(0,sb.length()-1));
    }

    /**
     * Prints the balance.
     */
    private void printBalance() {
        StringBuilder sb = new StringBuilder();
        Balance balance = (Balance) bot.retrieveValue(BALANCE_KEY);
        List<BalanceStatement> statements = balance.generateStatements();
        for (BalanceStatement bs : statements) {
            sb.append(String.format(BALANCE_STRING, bs.getDebtor().getName(), bs.getCreditor().getName(), bs
                    .getAmount()));
            sb.append(LINE_END);
        }
        bot.sendText("".equals(sb.toString()) ? "" : sb.toString().substring(0,sb.length()-1));
    }
}
