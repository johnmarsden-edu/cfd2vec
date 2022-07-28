fn main() {
    capnpc::CompilerCommand::new()
        .src_prefix("../../schema")
        .file("../../schema/message.capnp")
        .output_path("src/capnp")
        .default_parent_module(vec!["capnp".into()])
        .run()
        .expect("Message schema compilation command should succeed");
}
