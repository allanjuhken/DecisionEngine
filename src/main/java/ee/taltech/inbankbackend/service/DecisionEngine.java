package ee.taltech.inbankbackend.service;

import com.github.vladislavgoltjajev.personalcode.locale.estonia.EstonianPersonalCodeValidator;
import ee.taltech.inbankbackend.config.DecisionEngineConstants;
import ee.taltech.inbankbackend.exceptions.*;
import org.springframework.stereotype.Service;

import java.util.Calendar;

/**
 * A service class that provides a method for calculating an approved loan amount and period for a customer.
 * The loan amount is calculated based on the customer's credit modifier,
 * which is determined by the last four digits of their ID code.
 */
@Service
public class DecisionEngine {

    // Used to check for the validity of the presented ID code.
    private final EstonianPersonalCodeValidator validator = new EstonianPersonalCodeValidator();
    private int creditModifier = 0;

    /**
     * Calculates the maximum loan amount and period for the customer based on their ID code,
     * the requested loan amount and the loan period.
     * The loan period must be between 12 and 60 months (inclusive).
     * The loan amount must be between 2000 and 10000€ months (inclusive).
     *
     * @param personalCode ID code of the customer that made the request.
     * @param loanAmount Requested loan amount
     * @param loanPeriod Requested loan period
     * @return A Decision object containing the approved loan amount and period, and an error message (if any)
     * @throws InvalidPersonalCodeException If the provided personal ID code is invalid
     * @throws InvalidLoanAmountException If the requested loan amount is invalid
     * @throws InvalidLoanPeriodException If the requested loan period is invalid
     * @throws NoValidLoanException If there is no valid loan found for the given ID code, loan amount and loan period
     */

    public Decision calculateApprovedLoan(String personalCode, Long loanAmount, int loanPeriod)
            throws InvalidPersonalCodeException, InvalidLoanAmountException, InvalidLoanPeriodException,
            NoValidLoanException, InvalidAgeException {
        try {
            verifyInputs(personalCode, loanAmount, loanPeriod);

            // Check for debt, if personal codes last 4 digits are between 0000
            // and 2499, then person has debt and no loan is approved
            int i = Integer.parseInt(personalCode.substring(personalCode.length() - 4));
            if (i < 2500) {
                return new Decision(null, null, "Person has debt, no loan approved.");
            }

            // Determine credit modifier based on the last four digits of the personal code
            creditModifier = getCreditModifier(personalCode);

            // Calculate credit score
            double creditScore = (double) creditModifier / loanAmount * loanPeriod;

            // If credit score is less than 1 and requested loan amount can not be approved
            if (creditScore < 1) {
                // Find the maximum loan amount that can be approved
                while (creditScore < 1 && loanAmount < DecisionEngineConstants.MAXIMUM_LOAN_AMOUNT) {
                    loanAmount++;
                    creditScore = (double) creditModifier / loanAmount * loanPeriod;
                }

                // If a suitable loan amount is not found within the selected period, find a new suitable period
                while (creditScore < 1 && loanPeriod < DecisionEngineConstants.MAXIMUM_LOAN_PERIOD) {
                    loanPeriod++;
                    creditScore = (double) creditModifier / loanAmount * loanPeriod;
                }

                // If a suitable loan amount and period are not found, no loan approved
                if (creditScore < 1) {
                    return new Decision(null, null, "No suitable loan amount and period found, no loan approved.");
                }
            }

            // Return the maximum approved loan amount
            int approvedLoanAmount = Math.min(DecisionEngineConstants.MAXIMUM_LOAN_AMOUNT,
                    (int) Math.floor(creditScore * loanAmount));
            return new Decision(approvedLoanAmount, loanPeriod, null);

        } catch (Exception e) {
            return new Decision(null, null, e.getMessage());
        }
    }


    /**
     * Calculates the largest valid loan for the current credit modifier and loan period.
     *
     * @return Largest valid loan amount
     */
    private int highestValidLoanAmount(int loanPeriod) {
        return creditModifier * loanPeriod;
    }

    /**
     * Calculates the credit modifier of the customer to according to the last four digits of their ID code.
     * Debt - 0000...2499
     * Segment 1 - 2500...4999
     * Segment 2 - 5000...7499
     * Segment 3 - 7500...9999
     *
     * @param personalCode ID code of the customer that made the request.
     * @return Segment to which the customer belongs.
     */
    private int getCreditModifier(String personalCode) {
        int segment = Integer.parseInt(personalCode.substring(personalCode.length() - 4));

        if (segment < 2500) {
            return 0;
        } else if (segment < 5000) {
            return DecisionEngineConstants.SEGMENT_1_CREDIT_MODIFIER;
        } else if (segment < 7500) {
            return DecisionEngineConstants.SEGMENT_2_CREDIT_MODIFIER;
        }

        return DecisionEngineConstants.SEGMENT_3_CREDIT_MODIFIER;
    }

    /**
     * Verify that all inputs are valid according to business rules.
     * If inputs are invalid, then throws corresponding exceptions.
     *
     * @param personalCode Provided personal ID code
     * @param loanAmount Requested loan amount
     * @param loanPeriod Requested loan period
     * @throws InvalidPersonalCodeException If the provided personal ID code is invalid
     * @throws InvalidLoanAmountException If the requested loan amount is invalid
     * @throws InvalidLoanPeriodException If the requested loan period is invalid
     */
    private void verifyInputs(String personalCode, Long loanAmount, int loanPeriod)
            throws InvalidPersonalCodeException, InvalidLoanAmountException, InvalidLoanPeriodException {

        if (!validator.isValid(personalCode)) {
            throw new InvalidPersonalCodeException("Invalid personal ID code!");
        }
        if (!(DecisionEngineConstants.MINIMUM_LOAN_AMOUNT <= loanAmount)
                || !(loanAmount <= DecisionEngineConstants.MAXIMUM_LOAN_AMOUNT)) {
            throw new InvalidLoanAmountException("Invalid loan amount!");
        }
        if (!(DecisionEngineConstants.MINIMUM_LOAN_PERIOD <= loanPeriod)
                || !(loanPeriod <= DecisionEngineConstants.MAXIMUM_LOAN_PERIOD)) {
            throw new InvalidLoanPeriodException("Invalid loan period!");
        }
    }


    /**
    Verify that all inputs are valid according to business rules. Age restriction is added to the method.
    If inputs are invalid, then throws corresponding exceptions(Does not work properly).
     */
//    private void verifyInputs(String personalCode, Long loanAmount, int loanPeriod)
//            throws InvalidPersonalCodeException, InvalidLoanAmountException, InvalidLoanPeriodException, InvalidAgeException {
//
//        if (!validator.isValid(personalCode)) {
//            throw new InvalidPersonalCodeException("Invalid personal ID code!");
//        }
//
//        // Birth year from the personal code
//        int birthYear = Integer.parseInt(personalCode.substring(1, 3));
//        // Add 1900 to the birth year if the first digit of the personal code is less than 3
//        birthYear += (Integer.parseInt(personalCode.substring(0, 1)) < 3) ? 1800 : 1900;
//
//        // Calculate the current year
//        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
//
//        // Calculate the age of the customer
//        int age = currentYear - birthYear;
//
//        // Check if the age is within the approved range
//        if (age < 18 || age > (85 - DecisionEngineConstants.MAXIMUM_LOAN_PERIOD)) {
//            throw new InvalidAgeException("Out of age restrictions," + age + " years old, loan can not be approved!");
//        }
//
//        if (!(DecisionEngineConstants.MINIMUM_LOAN_AMOUNT <= loanAmount)
//                || !(loanAmount <= DecisionEngineConstants.MAXIMUM_LOAN_AMOUNT)) {
//            throw new InvalidLoanAmountException("Invalid loan amount!");
//        }
//        if (!(DecisionEngineConstants.MINIMUM_LOAN_PERIOD <= loanPeriod)
//                || !(loanPeriod <= DecisionEngineConstants.MAXIMUM_LOAN_PERIOD)) {
//            throw new InvalidLoanPeriodException("Invalid loan period!");
//        }
//    }

}
