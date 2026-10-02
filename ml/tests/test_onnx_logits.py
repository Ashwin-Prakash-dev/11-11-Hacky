import sys
import unittest
from pathlib import Path

import numpy as np
import onnx
import onnxruntime as ort
from onnx import TensorProto, helper, numpy_helper

REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT))

from ml.tools.onnx_logits import strip_final_softmax  # noqa: E402

W = np.array([[1.0, -2.0], [0.5, 3.0]], np.float32)
B = np.array([0.25, -0.75], np.float32)


def classifier(last_op="Softmax"):
    """x [N, 2] -> MatMul -> Add -> Softmax (or Relu) -> probs, like malaria_thin's ONNX graph."""
    nodes = [
        helper.make_node("MatMul", ["x", "W"], ["mm"]),
        helper.make_node("Add", ["mm", "B"], ["logits_in"]),
        helper.make_node(last_op, ["logits_in"], ["probs"]),
    ]
    graph = helper.make_graph(
        nodes, "tiny",
        [helper.make_tensor_value_info("x", TensorProto.FLOAT, ["N", 2])],
        [helper.make_tensor_value_info("probs", TensorProto.FLOAT, ["N", 2])],
        [numpy_helper.from_array(W, "W"), numpy_helper.from_array(B, "B")],
    )
    return helper.make_model(graph, opset_imports=[helper.make_opsetid("", 13)], ir_version=7)


def run(model, x):
    session = ort.InferenceSession(model.SerializeToString(), providers=["CPUExecutionProvider"])
    return session.get_outputs()[0].name, session.run(None, {"x": x})[0]


class StripFinalSoftmax(unittest.TestCase):
    def test_outputs_the_softmax_input_as_logits(self):
        x = np.array([[1.0, 2.0], [-0.5, 0.25]], np.float32)
        name, logits = run(strip_final_softmax(classifier()), x)
        self.assertEqual(name, "logits")
        np.testing.assert_allclose(logits, x @ W + B, rtol=1e-6)

    def test_softmax_of_logits_gives_the_original_probabilities(self):
        x = np.array([[3.0, -1.0]], np.float32)
        _, probs = run(classifier(), x)
        _, logits = run(strip_final_softmax(classifier()), x)
        e = np.exp(logits - logits.max(axis=1, keepdims=True))
        np.testing.assert_allclose(e / e.sum(axis=1, keepdims=True), probs, rtol=1e-6)

    def test_rejects_a_graph_that_does_not_end_in_softmax(self):
        with self.assertRaises(ValueError):
            strip_final_softmax(classifier("Relu"))

    def test_result_passes_the_onnx_checker(self):
        onnx.checker.check_model(strip_final_softmax(classifier()))


if __name__ == "__main__":
    unittest.main()
