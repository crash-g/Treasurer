package com.muciaccia.plugins.backend;

import com.muciaccia.plugins.Treasurer;
import com.muciaccia.plugins.backend.pojo.BalanceStatement;
import com.muciaccia.plugins.backend.pojo.User;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This class keeps an up-to-date balance, which contains a complete summary of all the credits and debts in the system.
 * Additionally, it calculates an optimal solution to the BALANCE PROBLEM, that is, the problem of giving money back
 * using the smallest possible number of transactions and minimizing the sum of the values of all transactions.
 * <p>
 * TERMINOLOGY:
 * BALANCED (group of users): a group of users for which the VALUE of the CREDITORS is equal to the value of the
 * DEBTORS.
 * <p>
 * CREDITOR: a user with a positive STATUS.
 * <p>
 * DEBTOR: a user with a negative STATUS.
 * <p>
 * GRAPH (of a solution): a graph which contains a vertex for each creditor and debtor, and an edge for each
 * TRANSACTION.
 * <p>
 * NORMALIZED STATUS: the absolute value of the STATUS of a user, multiplied by 100 to make it integer. This is
 * required by the algorithm which solves the BALANCE PROBLEM.
 * <p>
 * COMPONENT (of a solution): a group of users which corresponds to a connected component in the GRAPH of the
 * solution. Note that users in a component always form a balanced group.
 * <p>
 * SORTED (group of users): A group of users is SORTED when the users are sorted according to their NORMALIZED
 * STATUSES (in ascending order).
 * <p>
 * STATUS: the amount of money that a user should give or receive back to settle the balance.
 * The number is negative for debts.
 * <p>
 * TREE SOLUTION: a solution to the BALANCE PROBLEM which minimizes the amount of money exchanged and uses at most
 * n-1 transactions, where n is the total number of creditors and debtors. Such a solution can be easily computed but
 * is not necessarily optimal.
 * <p>
 * TRANSACTION: a single step of a solution to the BALANCE PROBLEM, corresponding to an exchange of money between
 * two users. Note that in an optimal solution a CREDITOR never gives money and a DEBTOR never receives it.
 * <p>
 * VALUE (of a set of users): the sum of the normalized statuses of the users in the set.
 * <p>
 * VALUE (of a component): the value of the creditors of the component (since a component is always balanced, this
 * will equal the value of the debtors).
 */
public class Balance {
    // contains the status of every user
    private final Map<User, BigDecimal> statuses;

    // contains the normalized status of every user
    private final Map<User, Integer> normalizedStatuses;

    public Balance() {
        statuses = new HashMap<>();
        normalizedStatuses = new HashMap<>();
    }

    /**
     * Updates the status of a user, summing the new credit/debt to the current status
     *
     * @param user   the user to update
     * @param update the amount of money to sum (negative in case of debts). Cannot be ZERO
     */
    void updateUserStatus(final User user, final BigDecimal update) {
        assert (!update.equals(Treasurer.ZERO));

        if (statuses.containsKey(user)) {
            BigDecimal updatedStatus = statuses.get(user).add(update);
            if (0 == updatedStatus.compareTo(Treasurer.ZERO)) {
                statuses.remove(user);
            } else {
                statuses.put(user, updatedStatus);
            }
        } else {
            statuses.put(user, update);
        }
    }

    /**
     * Solves the balance problem and outputs an optimal solution.
     *
     * @return a list of BalanceStatement which correspond to an optimal solution
     */
    public List<BalanceStatement> generateStatements() {
        if (statuses.isEmpty()) {
            // nothing to do, return an empty list
            return new ArrayList<>();
        }

        List<BalanceStatement> resultList = new ArrayList<>();
        List<User> creditors = new ArrayList<>();
        List<User> debtors = new ArrayList<>();
        normalizedStatuses.clear();

        // calculate all normalized statuses and populate sorted lists of creditors and debtors
        for (User user : statuses.keySet()) {
            if (statuses.get(user).compareTo(Treasurer.ZERO) > 0) {
                creditors.add(user);
            } else {
                debtors.add(user);
            }
            Integer normalizedStatus = statuses.get(user).abs().movePointRight(2).intValue();
            normalizedStatuses.put(user, normalizedStatus);
        }
        creditors.sort((u1, u2) -> normalizedStatuses.get(u1).compareTo(normalizedStatuses.get(u2)));
        debtors.sort((u1, u2) -> normalizedStatuses.get(u1).compareTo(normalizedStatuses.get(u2)));

        preProcessInput(creditors, debtors, resultList);

        // calculate value of the creditors (which will be equal to the value of the debtors)
        int totalSum = 0;
        for (User creditor : creditors) {
            totalSum += normalizedStatuses.get(creditor);
        }

        // initialize lists of components of an optimal solution
        List<List<User>> optimalCreditorsComponents = new ArrayList<>();
        List<List<User>> optimalDebtorsComponents = new ArrayList<>();
        if(!creditors.isEmpty()) {
            // optimization: note that no component can have smaller value than the smallest value of a user, so
            // initialize targetSum accordingly
            int targetSum = Math.max(normalizedStatuses.get(creditors.get(0)), normalizedStatuses.get(debtors.get(0)));

            computeOptimalSolution(creditors, debtors, 2, totalSum, targetSum, optimalCreditorsComponents,
                    optimalDebtorsComponents);
        }

        // compute tree solutions for each component
        for (int i = 0; i < optimalCreditorsComponents.size(); ++i) {
            resultList.addAll(computeTreeSolution(optimalCreditorsComponents.get(i), optimalDebtorsComponents.get(i)));
        }
        return resultList;
    }

    /**
     * Applies a reduction rule that reduces the size of the input, using the fact that if a debtor and a creditor have
     * exactly the same value then there exists an optimal solution where they form a component.
     *
     * @param creditors  a sorted list of creditors
     * @param debtors    a sorted list of debtors
     * @param resultList a list of transactions that can be extended to form an optimal solution
     */
    private void preProcessInput(final List<User> creditors, final List<User> debtors, List<BalanceStatement>
            resultList) {
        Iterator<User> creditorsIter = creditors.iterator();
        while (creditorsIter.hasNext()) {
            User creditor = creditorsIter.next();
            if (normalizedStatuses.get(creditor).compareTo(normalizedStatuses.get(debtors.get(debtors.size() - 1))) >
                    0) {
                // each remaining creditor has higher value than each debtor: no more match can be found
                break;
            }
            Iterator<User> debtorIter = debtors.iterator();
            while (debtorIter.hasNext()) {
                User debtor = debtorIter.next();
                int compare = normalizedStatuses.get(creditor).compareTo(normalizedStatuses.get(debtor));
                if (compare < 0) {
                    // each remaining debtor has higher value than the current creditor: change creditor
                    break;
                } else if (0 == compare) {
                    // remove pairs with same value
                    resultList.add(new BalanceStatement(debtor, creditor, statuses.get(creditor)));
                    debtorIter.remove();
                    creditorsIter.remove();
                    break;
                }
            }
        }
    }

    /**
     * Computes a tree solution for a BALANCED group of creditors and debtors, using a greedy algorithm.
     *
     * @param creditors a list of creditors
     * @param debtors   a list of debtors
     * @return a list of BalanceStatement which describes the transactions required to solve the problem
     */
    private List<BalanceStatement> computeTreeSolution(final List<User> creditors, final List<User> debtors) {
        List<BalanceStatement> statements = new ArrayList<>();
        List<User> creditorsCopy = new ArrayList<>(creditors);
        List<User> debtorsCopy = new ArrayList<>(debtors);
        Map<User, BigDecimal> statusesCopy = new HashMap<>(statuses);
        while (!creditorsCopy.isEmpty()) {
            BigDecimal credit = statusesCopy.get(creditorsCopy.get(0));
            BigDecimal debit = statusesCopy.get(debtorsCopy.get(0)).negate();
            int compare = credit.compareTo(debit);
            if (compare > 0) {
                statements.add(new BalanceStatement(debtorsCopy.get(0), creditorsCopy.get(0), debit));
                statusesCopy.put(creditorsCopy.get(0), credit.subtract(debit));
                debtorsCopy.remove(0);
            } else if (compare < 0) {
                statements.add(new BalanceStatement(debtorsCopy.get(0), creditorsCopy.get(0), credit));
                statusesCopy.put(debtorsCopy.get(0), credit.subtract(debit));
                creditorsCopy.remove(0);
            } else {
                statements.add(new BalanceStatement(debtorsCopy.get(0), creditorsCopy.get(0), debit));
                creditorsCopy.remove(0);
                debtorsCopy.remove(0);
            }
        }
        return statements;
    }

    /**
     * The base step of the balance algorithm. Given a list of user and a subset of this list, adds users to the
     * subset as long as a target value has not been reached.
     *
     * @param userList      a list of users, either creditors or debtors
     * @param partialResult a subset of this list, in the form of a stack of indexes
     * @param target        a target value which cannot be surpassed
     * @return 0 if the target has been reached. Otherwise, a positive integer corresponding to the
     * difference between the target value and the value of the users that were added to the subset.
     */
    private int baseStep(final List<User> userList, final Stack<Integer> partialResult, int target) {
        int currentIndex = partialResult.pop();
        while (currentIndex < userList.size()) {
            int currentValue = normalizedStatuses.get(userList.get(currentIndex));
            if (currentValue < target) {
                partialResult.push(currentIndex);
                target -= currentValue;
                ++currentIndex;
            } else if (currentValue == target) {
                partialResult.push(currentIndex);
                return 0;
            } else {
                return target;
            }
        }
        return target;
    }

    /**
     * Modified version of the Subset Sum problem. Computes all subsets of users which correspond to the given sum.
     *
     * @param userList a non-empty list of users, either creditors or debtors. The list MUST be sorted
     * @param sum      the target sum
     * @return a list of lists of users, for which the value is equal to the sum given in input
     */
    private List<List<User>> computeAllSubsets(final List<User> userList, int sum) {
        assert (!userList.isEmpty());

        List<List<User>> resultList = new ArrayList<>();
        Stack<Integer> subset = new Stack<>();
        subset.push(0);
        do {
            sum = baseStep(userList, subset, sum);
            if (0 == sum) {
                // found a result, copy it to a list and save it in the list of results
                List<User> result = subset.stream().map(userList::get).collect(Collectors.toList());
                resultList.add(result);
            }
            // does not matter if we found a result or not, continue the search popping the last element and
            // inserting the next one
            while (!subset.isEmpty()) {
                int index = subset.pop();
                sum += normalizedStatuses.get(userList.get(index));
                if (++index < userList.size()) {
                    subset.push(index);
                    break;
                }
            }
            // loop as long as the stack is not empty and the first element of the stack does not have a value higher
            // than the target sum
        } while (!subset.isEmpty() && normalizedStatuses.get(userList.get(subset.get(0))) <= sum);
        return resultList;
    }

    /**
     * Computes an optimal solution to the balance problem restricted to the two BALANCED lists of creditors and
     * debtors given in input.
     *
     * @param creditors           a sorted list of creditors
     * @param debtors             a sorted list of debtors
     * @param targetComponents    the number of components of the best solution calculated so far, plus 1
     * @param totalSum            the value of the list of creditors (which is equal to the value of the list of
     *                            debtors)
     * @param targetSum           the smallest value of a component in a solution
     * @param creditorsComponents a list of lists of creditors, each corresponding to a component in an optimal solution
     * @param debtorsComponents   a list of lists of debtors, each corresponding to a component in an optimal solution
     */
    private void computeOptimalSolution(final List<User> creditors, final List<User> debtors, int targetComponents,
                                        final int totalSum, int targetSum, final List<List<User>>
                                                creditorsComponents, final List<List<User>> debtorsComponents) {
        // default solution is the one containing only one component
        creditorsComponents.add(creditors);
        debtorsComponents.add(debtors);

        // since we always look for the partition with the smallest value, no need to look for a value
        // larger than the total value divided by the number of target components
        while (targetSum <= Math.floor(totalSum / targetComponents)) {
            // compute all groups of users with value equal to targetSum
            List<List<User>> creditorsCandidates = computeAllSubsets(creditors, targetSum);
            List<List<User>> debtorsCandidates = computeAllSubsets(debtors, targetSum);
            // loop through all the results and recursively look for an optimal solution
            for (List<User> creditorCandidate : creditorsCandidates) {
                for (List<User> debtorCandidate : debtorsCandidates) {
                    // prepare lists for the recursive step
                    List<User> leftCreditorCandidates = new ArrayList<>(creditors);
                    leftCreditorCandidates.removeAll(creditorCandidate);
                    List<User> leftDebtorsCandidates = new ArrayList<>(debtors);
                    leftDebtorsCandidates.removeAll(debtorCandidate);
                    List<List<User>> candidateCreditorsSolution = new ArrayList<>();
                    List<List<User>> candidateDebtorsSolution = new ArrayList<>();
                    // note that by minimality of the partition we have found, we know that the target sum for the
                    // restricted problem is at least the current target sum
                    int sum = Math.max(targetSum, Math.max(normalizedStatuses.get(leftCreditorCandidates.get(0)),
                            normalizedStatuses.get(leftDebtorsCandidates.get(0))));
                    // recursively compute an optimal solution
                    computeOptimalSolution(leftCreditorCandidates, leftDebtorsCandidates, Math.max(targetComponents -
                            1, 2), totalSum - targetSum, sum, candidateCreditorsSolution, candidateDebtorsSolution);

                    if (candidateCreditorsSolution.size() >= creditorsComponents.size()) {
                        // the solution that was found is better than the current solution, update it
                        creditorsComponents.clear();
                        debtorsComponents.clear();
                        creditorsComponents.add(creditorCandidate);
                        debtorsComponents.add(debtorCandidate);
                        creditorsComponents.addAll(candidateCreditorsSolution);
                        debtorsComponents.addAll(candidateDebtorsSolution);
                        // now we are only interested in solutions that have at least one additional component
                        targetComponents = creditorsComponents.size() + 1;
                    }
                }
            }
            // increase the target sum and check if this yields a better solution
            ++targetSum;
        }
    }
}
