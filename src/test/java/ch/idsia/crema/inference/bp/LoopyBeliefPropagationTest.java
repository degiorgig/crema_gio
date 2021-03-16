package ch.idsia.crema.inference.bp;

import ch.idsia.crema.entropy.BayesianEntropy;
import ch.idsia.crema.factor.bayesian.BayesianFactor;
import ch.idsia.crema.inference.BayesianNetworkContainer;
import ch.idsia.crema.inference.ve.FactorVariableElimination;
import ch.idsia.crema.inference.ve.VariableElimination;
import ch.idsia.crema.model.graphical.BayesianNetwork;
import ch.idsia.crema.model.io.bif.BIFObject;
import ch.idsia.crema.model.io.bif.BIFParser;
import ch.idsia.crema.model.io.dot.DotSerialize;
import gnu.trove.map.TIntIntMap;
import gnu.trove.map.hash.TIntIntHashMap;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Author:  Claudio "Dna" Bonesana
 * Project: crema
 * Date:    01.03.2021 18:42
 */
public class LoopyBeliefPropagationTest {

	@Test
	public void testPropagationQuery() {
		// source: Jensen, p.110, Fig. 4.1 "A simple Bayesian network BN".
		BayesianNetwork model = new BayesianNetwork();
		int A0 = model.addVariable(2);
		int A1 = model.addVariable(2);
		int A2 = model.addVariable(2);
		int A3 = model.addVariable(2);
		int A4 = model.addVariable(2);
		int A5 = model.addVariable(2);

		model.addParent(A1, A0);
		model.addParent(A2, A0);
		model.addParent(A3, A1);
		model.addParent(A4, A1);
		model.addParent(A4, A2);
		model.addParent(A5, A2);

		BayesianFactor[] factors = new BayesianFactor[model.getVariables().length];

		factors[A0] = new BayesianFactor(model.getDomain(A0), new double[]{.7, .3});
		factors[A1] = new BayesianFactor(model.getDomain(A0, A1), new double[]{.4, .3, .6, .7});
		factors[A2] = new BayesianFactor(model.getDomain(A0, A2), new double[]{.5, .8, .5, .2});
		factors[A3] = new BayesianFactor(model.getDomain(A1, A3), new double[]{.6, .1, .4, .9});
		factors[A4] = new BayesianFactor(model.getDomain(A1, A2, A4), new double[]{.1, .8, .4, .7, .9, .2, .6, .3});
		factors[A5] = new BayesianFactor(model.getDomain(A2, A5), new double[]{.4, .5, .6, .5});

		model.setFactors(factors);

		LoopyBeliefPropagation<BayesianFactor> lbp = new LoopyBeliefPropagation<>();
		lbp.setIterations(1);

		/*
		TODO: we are using pre-processing so these checks are not valid anymore
		for (int i : model.getVariables()) {
			for (int j : model.getVariables()) {
				if (i == j) continue;

				final DefaultEdge edge = model.getNetwork().getEdge(i, j);
				if (edge == null) continue;

				var key = new ImmutablePair<>(i, j);

				assertTrue(lbp.neighbours.containsKey(key));
			}
		}

		assertEquals(lbp.messages.size(), lbp.neighbours.size());
		 */

		BayesianFactor factor = lbp.query(model, A0);
		assertEquals(factors[A0], factor);
	}

	@Test
	public void testCollectingEvidenceWithObs() {
		BayesianNetwork model = new BayesianNetwork();
		int A = model.addVariable(2);
		int B = model.addVariable(2);
		int C = model.addVariable(2);

		model.addParent(B, A);
		model.addParent(C, A);

		BayesianFactor[] factors = new BayesianFactor[model.getVariables().length];

		factors[A] = new BayesianFactor(model.getDomain(A), new double[]{.4, .6});
		factors[B] = new BayesianFactor(model.getDomain(A, B), new double[]{.3, .9, .7, .1});
		factors[C] = new BayesianFactor(model.getDomain(A, C), new double[]{.2, .7, .8, .3});

		model.setFactors(factors);

		LoopyBeliefPropagation<BayesianFactor> lbp = new LoopyBeliefPropagation<>();

		// P(A):
		TIntIntHashMap obs = new TIntIntHashMap();
		BayesianFactor q = lbp.query(model, obs, A);
		System.out.println("P(A):              " + q);
		assertArrayEquals(new double[]{.4, .6}, q.getData(), 1e-6);

		// P(A | B=0)
		obs.put(B, 0);
		q = lbp.query(model, obs, A);
		System.out.println("P(A | B=0):       " + q);
		assertArrayEquals(new double[]{.1818, .8182}, q.getData(), 1e-3);

		// P(A | B=1)
		obs.put(B, 1);
		q = lbp.query(model, obs, A);
		System.out.println("P(A | B=1):       " + q);
		assertArrayEquals(new double[]{.8235, .1765}, q.getData(), 1e-3);

		// P(A | B=0, C=0)
		obs.put(B, 0);
		obs.put(C, 0);
		q = lbp.query(model, obs, A);
		System.out.println("P(A | B=0, C=0): " + q);
		assertArrayEquals(new double[]{.0597, .9403}, q.getData(), 1e-3);

		// P(A | B=1, C=1)
		obs.put(B, 1);
		obs.put(C, 1);
		q = lbp.query(model, obs, A);
		System.out.println("P(A | B=1, C=1): " + q);
		assertArrayEquals(new double[]{.9256, .0744}, q.getData(), 1e-3);
	}

	@Test
	public void testBayesianNetworkFromExercise41() {
		BayesianNetwork bn = new BayesianNetwork();
		int A = bn.addVariable(2);
		int B = bn.addVariable(2);
		int C = bn.addVariable(2);
		int D = bn.addVariable(2);

		bn.addParent(B, A);
		bn.addParent(C, B);
		bn.addParent(D, C);

		BayesianFactor[] factors = new BayesianFactor[bn.getVariables().length];
		factors[A] = new BayesianFactor(bn.getDomain(A), new double[]{.2, .8});
		factors[B] = new BayesianFactor(bn.getDomain(A, B), new double[]{.2, .6, .8, .4});
		factors[C] = new BayesianFactor(bn.getDomain(B, C), new double[]{.3, .2, .7, .8});
		factors[D] = new BayesianFactor(bn.getDomain(C, D), new double[]{.9, .6, .1, .4});

		bn.setFactors(factors);

		// computations by hand
		final BayesianFactor phi1 = factors[D].filter(D, 0);   // Sum_D P(D|C) * e_d
		final BayesianFactor phi2 = factors[C].combine(phi1);       // P(C|B) * phi1
		final BayesianFactor phi3 = phi2.marginalize(C);            // Sum_C phi2
		final BayesianFactor phi4 = factors[B].combine(phi3);       // P(B|A) * phi3
		final BayesianFactor phi5 = phi4.marginalize(B);            // Sum_B phi4
		final BayesianFactor phi6 = factors[A].combine(phi5);       // P(A) * phi5
		final BayesianFactor res1 = phi6.normalize();

		// computations by hand using messages
		final BayesianFactor psi1 = factors[D].filter(D, 0);
		final BayesianFactor psi2 = factors[B].combine(factors[C]).combine(psi1).marginalize(C);
		final BayesianFactor phiS = factors[A].combine(psi2).marginalize(B);
		final BayesianFactor res = phiS.normalize();

		assertEquals(res1, res);

		// computation using Belief Propagation
		LoopyBeliefPropagation<BayesianFactor> inf = new LoopyBeliefPropagation<>();

		TIntIntMap obs = new TIntIntHashMap();
		obs.put(D, 0);
		final BayesianFactor q = inf.query(bn, obs, A);
		System.out.println("query=" + q);

		assertEquals(res, q);
	}

	@Test
	public void testNumberOfStatesReturned() throws Exception {
		final BayesianNetwork network = BIFParser.read("models/bif/alloy.bif").network;
		final LoopyBeliefPropagation<BayesianFactor> lbp = new LoopyBeliefPropagation<>();

//		int[] vs = {4, 5, 25};

		for (int v : network.getVariables()) {
			final BayesianFactor q0 = lbp.query(network, v);
//			System.out.println(v + ":\t" + q0.getData().length + "\t" + network.getSize(v));
//			System.out.println(q0);

			assertEquals(network.getSize(v), q0.getData().length);
		}
	}

	@Test
	void testVariableElimination() {
		final BayesianNetwork model = BayesianNetworkContainer.mix5Variables().network;

		final VariableElimination<BayesianFactor> ve = new FactorVariableElimination<>(new int[]{4, 3, 1, 0, 2});
		final LoopyBeliefPropagation<BayesianFactor> lbp = new LoopyBeliefPropagation<>();

		TIntIntMap evidence;
		BayesianFactor Qlbp;
		BayesianFactor Qve;

		evidence = new TIntIntHashMap();
		Qlbp = lbp.query(model, evidence, 2);
		Qve = ve.query(model, evidence, 2);
		System.out.println("LBP: P(Rain) =                                     " + Qlbp);
		System.out.println("VE:  P(Rain) =                                     " + Qve);

		assertEquals(Qlbp.getValue(0), Qve.getValue(0), 0.01);

		evidence = new TIntIntHashMap();
		evidence.put(3, 0);
		evidence.put(4, 1);
		Qlbp = lbp.query(model, evidence, 2);
		Qve = ve.query(model, evidence, 2);
		System.out.println("LBP: P(Rain|Wet Grass = false, Slippery = true) =  " + Qlbp);
		System.out.println("VE:  P(Rain|Wet Grass = false, Slippery = true) =  " + Qve);

		assertEquals(Qlbp.getValue(0), Qve.getValue(0), 0.05);

		evidence = new TIntIntHashMap();
		evidence.put(3, 0);
		evidence.put(4, 0);
		Qlbp = lbp.query(model, evidence, 2);
		Qve = ve.query(model, evidence, 2);
		System.out.println("LBP: P(Rain|Wet Grass = false, Slippery = false) = " + Qlbp);
		System.out.println("VE:  P(Rain|Wet Grass = false, Slippery = false) = " + Qve);

		assertEquals(Qlbp.getValue(0), Qve.getValue(0), 0.01);

		evidence = new TIntIntHashMap();
		evidence.put(0, 1);
		Qlbp = lbp.query(model, evidence, 2);
		Qve = ve.query(model, evidence, 2);
		System.out.println("LBP: P(Rain|Winter = true) =                       " + Qlbp);
		System.out.println("VE:  P(Rain|Winter = true) =                       " + Qve);

		assertEquals(Qlbp.getValue(0), Qve.getValue(0), 0.01);

		evidence = new TIntIntHashMap();
		evidence.put(0, 0);
		Qlbp = lbp.query(model, evidence, 2);
		Qve = ve.query(model, evidence, 2);
		System.out.println("LBP: P(Rain|Winter = false) =                      " + Qlbp);
		System.out.println("VE:  P(Rain|Winter = false) =                      " + Qve);

		assertEquals(Qlbp.getValue(0), Qve.getValue(0), 0.01);
	}

	@Disabled
	@Test
	public void testExactInference() throws Exception {
		final BIFObject bif = BIFParser.read("models/bif/bnD.em.bif");
		final BayesianNetwork model = bif.network;
		final Function<String, Integer> v = (String s) -> bif.variableName.get(s);

		final LoopyBeliefPropagation<BayesianFactor> lbp = new LoopyBeliefPropagation<>();
		lbp.setIterations(10);

		final String[] tNames = new String[]{"distress_1", "lack_vit_1", "psico_event2_1"};

		final int[] targets = Arrays.stream(tNames).map(v).mapToInt(x -> x).toArray();

		final BayesianFactor[] fTargets = new BayesianFactor[tNames.length];

		for (int i = 0; i < tNames.length; i++) {
			final String name = tNames[i];
			final int target = targets[i];
			fTargets[i] = lbp.query(model, target);
			System.out.printf("\t%2d %15s: %s%n", target, name, fTargets[i]);
			Files.write(Paths.get("graph." + name + "." + target + ".dot"), new DotSerialize().run(lbp.model).getBytes());
		}

		double H = Arrays.stream(fTargets).mapToDouble(BayesianEntropy::H).sum() / fTargets.length;
		System.out.printf("\t   H:              %s%n", H); //

		assertEquals(0.2549172, H, 0.01, "Entropy for skills");

		double[] expected = new double[]{0.920117, 0.949444, 0.990979};
		for (int i = 0; i < targets.length; i++)
			assertEquals(expected[i], fTargets[i].getValue(0), .01, "Variable: " + tNames[i]);

		final String[] qNames = new String[]{"noanxiety_0", "illness_0", "ISCO_0", "energy_0"};
		final int[] qTargets = Arrays.stream(qNames).mapToInt(bif.variableName::get).toArray();

		expected = new double[]{0.2250806, 0.2531826, 0.2538594, 0.2353488};

		for (int q = 0; q < qNames.length; q++) {
			final String qName = qNames[q];
			final int qTarget = qTargets[q];
			final int states = model.getSize(qTarget);

			final TIntIntMap ev = new TIntIntHashMap();

			final double[] h = new double[states];
			final double[] pa = new double[states];

			final BayesianFactor pQ = lbp.query(model, qTarget);

			// for (i in 1:length(states))
			for (int i = 0; i < states; i++) {
				ev.put(qTarget, i);

				// for (t in 1:length(target))
				final BayesianFactor[] p = Arrays.stream(targets)
						.mapToObj(t -> lbp.query(model, ev, t))
						.toArray(BayesianFactor[]::new);

				h[i] = Arrays.stream(p).mapToDouble(BayesianEntropy::H).sum() / fTargets.length;
				pa[i] = pQ.getValue(i);
			}

			final double ch = IntStream.range(0, states)
					.mapToDouble(i -> h[i] * pa[i])
					.sum();

			System.out.printf("\t%2d %15s %.4f%n", qTarget, qName, ch);

			assertEquals(expected[q], ch, .01, "Variable: " + qName);
		}

		System.out.println("choose variable noanxiety_0 -> 10");

		final TIntIntMap obs = new TIntIntHashMap();
		obs.put(v.apply("noanxiety_0"), 10);

		expected = new double[]{0.083890728, 0.056563958, 0.009261867};

		for (int i = 0; i < tNames.length; i++) {
			final String name = tNames[i];
			final int target = targets[i];
			fTargets[i] = lbp.query(model, obs, target);

			assertEquals(expected[i], fTargets[i].getValue(0), .01, "Variable: " + name);
		}
		H = Arrays.stream(fTargets).mapToDouble(BayesianEntropy::H).sum() / fTargets.length;

		System.out.printf("\t   H:              %s%n", H);
		assertEquals(0.1388963, H, .01);
	}
}