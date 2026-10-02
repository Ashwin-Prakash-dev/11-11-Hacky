"""Remove a classifier's final Softmax so the ONNX model outputs logits.

The engine's ClassifierDecoder applies softmax itself for `output.decoder: softmax` (the smoke golden encodes the
same convention), so a pack model must not apply it again.

    python ml/tools/onnx_logits.py in.onnx out.onnx
"""
import sys

import onnx


def strip_final_softmax(model: onnx.ModelProto) -> onnx.ModelProto:
    """Returns a copy whose single output, renamed `logits`, is the input of the final Softmax node."""
    out = onnx.ModelProto()
    out.CopyFrom(model)
    graph = out.graph
    if len(graph.output) != 1:
        raise ValueError(f"expected one graph output, found {len(graph.output)}")
    output = graph.output[0]
    producers = [n for n in graph.node if output.name in n.output]
    if len(producers) != 1 or producers[0].op_type != "Softmax":
        raise ValueError(f"graph output '{output.name}' is not produced by a Softmax node")
    softmax = producers[0]
    logits = softmax.input[0]
    graph.node.remove(softmax)
    for node in graph.node:                       # rename the tensor so the output is called `logits`
        node.output[:] = ["logits" if o == logits else o for o in node.output]
        node.input[:] = ["logits" if i == logits else i for i in node.input]
    output.name = "logits"
    onnx.checker.check_model(out)
    return out


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    onnx.save(strip_final_softmax(onnx.load(sys.argv[1])), sys.argv[2])
