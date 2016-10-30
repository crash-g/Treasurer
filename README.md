This is an implementation of a treasurer plugin for a generic bot. The plugin registers the expenses of a group of users and provides an optimized way of giving money back with the minimum possible number of transactions.

Allowed messages include: BALANCE (shows a summary of debts), HISTORY (shows a summary of user debts and credits), messages to add expenses and messages to manage groups of users.

In particular, messages to add expenses should have the form AMOUNT|HANDLE[,HANDLE]*[ "MESSAGE"], where HANDLE is a user name or a group name with optional modifiers (+ and *) to allow uneven splitting.
