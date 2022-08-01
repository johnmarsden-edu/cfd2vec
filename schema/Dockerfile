FROM rust:latest

RUN mkdir -p /usr/local/include/capnp/

# Install capnproto
WORKDIR /app/build/
RUN curl -O https://capnproto.org/capnproto-c++-0.10.2.tar.gz
RUN tar zxf capnproto-c++-0.10.2.tar.gz
WORKDIR /app/build/capnproto-c++-0.10.2
RUN ./configure
RUN make -j6 check
RUN make install

# Build and install capnproto-rust
WORKDIR /app/build/
RUN cargo install capnpc
RUN git clone https://github.com/capnproto/capnproto-rust.git rust
RUN cp /app/build/rust/capnpc/rust.capnp /usr/local/include/capnp/

# Build and install capnproto-java
RUN git clone https://github.com/capnproto/capnproto-java.git java
WORKDIR /app/build/java
RUN make all && make install

# Mount volume for schema to be loaded to
WORKDIR /app/schema/
COPY *.capnp /app/schema/

RUN mkdir -p /app/codegen/rust
RUN capnp compile -I /usr/local/include -orust:/app/codegen/rust/ *.capnp

RUN mkdir -p /app/codegen/java
RUN capnp compile -I /usr/local/include -ojava:/app/codegen/java/ *.capnp
