use colored::Colorize;
use itertools::Itertools;
use nu_pretty_hex::pretty_hex;
use petgraph::graph::Graph;
use petgraph_graph_tool::attr_types::{GtString, Int32};
use petgraph_graph_tool::{GraphTool, GraphToolAttribute};

#[tokio::test]
async fn lesmis_network() -> Result<(), Box<dyn std::error::Error + 'static>> {
    let mut deps = Graph::<String, u8>::new();
    let pg = deps.add_node("petgraph".to_string());
    let fb = deps.add_node("fixedbitset".to_string());
    let qc = deps.add_node("quickcheck".to_string());
    let rand = deps.add_node("rand".to_string());
    let libc = deps.add_node("libc".to_string());
    deps.extend_with_edges(&[
        (pg, fb, 0),
        (pg, qc, 1),
        (qc, rand, 2),
        (rand, libc, 3),
        (qc, libc, 4),
    ]);

    let mut gt_printer = GraphTool::<Graph<_, _>>::new(deps);
    // test.add_display_node_attribute();
    gt_printer.add_node_attribute::<GtString>(GraphToolAttribute::new(
        "name",
        Box::new(|n| n.to_string().into()),
    ));

    gt_printer.add_edge_attribute::<Int32>(GraphToolAttribute::new(
        "number",
        Box::new(|e| i32::from(*e).into()),
    ));

    let mut output: Vec<u8> = Vec::new();

    gt_printer.write_to(&mut output)?; // Comment ends after 138 bytes

    let gt_output: Vec<u8> = tokio::fs::read("tests/gt.gt").await?; // Comment ends after 197 bytes

    println!("The output from my implementation");
    println!("{}", pretty_hex(&output));

    println!("The output from the gt implementation");
    println!("{}", pretty_hex(&gt_output));

    for (index, os) in output[138..]
        .iter()
        .zip_longest(gt_output[197..].iter())
        .enumerate()
    {
        match os {
            itertools::EitherOrBoth::Both(left, right) => {
                if left == right {
                    println!("{}: {}", index, left.to_string().bright_green())
                } else {
                    println!(
                        "{}: {}",
                        index,
                        format!("{} != {}", left, right).bright_red()
                    )
                }
            }
            itertools::EitherOrBoth::Left(rem) | itertools::EitherOrBoth::Right(rem) => {
                println!("{}: {}", index, rem.to_string().bright_blue())
            }
        }
    }

    tokio::fs::write("tests/petgraph.gt", output).await?;
    Ok(())
}
