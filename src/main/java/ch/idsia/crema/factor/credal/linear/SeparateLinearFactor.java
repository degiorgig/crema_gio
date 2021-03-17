package ch.idsia.crema.factor.credal.linear;


import ch.idsia.crema.factor.credal.SeparatelySpecified;
import org.apache.commons.math3.optim.linear.LinearConstraintSet;

public interface SeparateLinearFactor<F extends SeparateLinearFactor<F>> extends SeparatelySpecified<F>, LinearFactor {

	/**
	 *
	 * @param states
	 * @return
	 */
	LinearConstraintSet getLinearProblem(int... states);

	/**
	 *
	 * @param offset
	 * @return
	 */
	LinearConstraintSet getLinearProblemAt(int offset);

}
