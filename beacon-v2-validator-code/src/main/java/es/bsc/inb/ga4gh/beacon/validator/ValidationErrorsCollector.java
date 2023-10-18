package es.bsc.inb.ga4gh.beacon.validator;

import java.util.List;

/**
 * ValidationObserver implementation that just collects validation errors.
 * 
 * @author Dmitry Repchevsky
 */

public class ValidationErrorsCollector implements ValidationObserver {

    final List<BeaconValidationMessage> errors;

    public ValidationErrorsCollector(List<BeaconValidationMessage> errors) {
        this.errors = errors;
    }
    
    @Override
    public void error(BeaconValidationMessage error) {
        errors.add(error);
    }
}
