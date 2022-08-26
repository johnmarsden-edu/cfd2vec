// This file was generated with `cornucopia`. Do not modify.
    #![allow(clippy::all, clippy::pedantic)]
    #![allow(unused_variables)]
    #![allow(unused_imports)]
    #![allow(dead_code)]
    pub mod types { pub mod public { #[derive( Debug, postgres_types::ToSql, postgres_types::FromSql, Clone, Copy, PartialEq, Eq)]
                        #[postgres(name = "edge_type")]
                        pub enum EdgeType { Statement,Decision,Exception }
#[derive( Debug, postgres_types::ToSql, postgres_types::FromSql, Clone, Copy, PartialEq, Eq)]
                        #[postgres(name = "node_type")]
                        pub enum NodeType { Source,Sink,Statement,Control,Decision,Exception } } }pub mod queries { pub mod edge { use futures::{{StreamExt, TryStreamExt}};use futures; use cornucopia_async::GenericClient; 



#[derive(Debug)]
            pub struct InsertEdgeParams<'a> { pub graph_id : i32,pub source : i32,pub target : i32,pub edge_type : super::super::types::public::EdgeType,pub direction : Option<bool>,pub exception : Option<&'a str> } #[derive( Debug, Clone, PartialEq,)] pub struct Edge { pub id : i32,pub graph_id : i32,pub source : i32,pub target : i32,pub edge_type : super::super::types::public::EdgeType,pub direction : Option<bool>,pub exception : Option<String> }pub struct EdgeBorrowed<'a> { pub id : i32,pub graph_id : i32,pub source : i32,pub target : i32,pub edge_type : super::super::types::public::EdgeType,pub direction : Option<bool>,pub exception : Option<&'a str> }
                impl<'a> From<EdgeBorrowed<'a>> for Edge {
                    fn from(EdgeBorrowed { id,graph_id,source,target,edge_type,direction,exception }: EdgeBorrowed<'a>) -> Self {
                        Self { id,graph_id,source,target,edge_type,direction,exception: exception.map(|v| v.into()) }
                    }
                }
            pub struct EdgeQuery<'a, C: GenericClient, T, const N: usize> {
                client: &'a  C,
                params: [&'a (dyn postgres_types::ToSql + Sync); N],
                stmt: &'a mut cornucopia_async::private::Stmt,
                extractor: fn(&tokio_postgres::Row) -> EdgeBorrowed,
                mapper: fn(EdgeBorrowed) -> T,
            }
            impl<'a, C, T:'a, const N: usize> EdgeQuery<'a, C, T, N> where C: GenericClient {
                pub fn map<R>(self, mapper: fn(EdgeBorrowed) -> R) -> EdgeQuery<'a,C,R,N> {
                    EdgeQuery {
                        client: self.client,
                        params: self.params,
                        stmt: self.stmt,
                        extractor: self.extractor,
                        mapper,
                    }
                }
            
                pub async fn one(self) -> Result<T, tokio_postgres::Error> {
                    let stmt = self.stmt.prepare(self.client).await?;
                    let row = self.client.query_one(stmt, &self.params).await?;
                    Ok((self.mapper)((self.extractor)(&row)))
                }
            
                pub async fn all(self) -> Result<Vec<T>, tokio_postgres::Error> {
                    self.iter().await?.try_collect().await
                }
            
                pub async fn opt(self) -> Result<Option<T>, tokio_postgres::Error> {
                    let stmt = self.stmt.prepare(self.client).await?;
                    Ok(self
                        .client
                        .query_opt(stmt, &self.params)
                        .await?
                        .map(|row| (self.mapper)((self.extractor)(&row))))
                }
            
                pub async fn iter(
                    self,
                ) -> Result<impl futures::Stream<Item = Result<T, tokio_postgres::Error>> + 'a, tokio_postgres::Error> {
                    let stmt = self.stmt.prepare(self.client).await?;
                    let it = self
                        .client
                        .query_raw(stmt, cornucopia_async::private::slice_iter(&self.params))
                        .await?
                        
                        .map(move |res| res.map(|row| (self.mapper)((self.extractor)(&row))))
                        .into_stream();
                    Ok(it)
                }
            } pub fn program_edges() -> ProgramEdgesStmt {
                ProgramEdgesStmt(cornucopia_async::private::Stmt::new("SELECT id, graph_id, source, target, edge_type, direction, exception
FROM edges
WHERE graph_id IN (
    SELECT id
    FROM graphs
    WHERE graphs.program_id = $1
)"))
            }
            pub struct ProgramEdgesStmt(cornucopia_async::private::Stmt);
            impl ProgramEdgesStmt {pub fn bind<'a, C: GenericClient>(&'a mut self, client: &'a  C, program_id : &'a i32) -> EdgeQuery<'a,C, Edge, 1> {
                EdgeQuery {
                    client,
                    params: [program_id],
                    stmt: &mut self.0,
                    extractor: |row| { EdgeBorrowed {id: row.get(0),graph_id: row.get(1),source: row.get(2),target: row.get(3),edge_type: row.get(4),direction: row.get(5),exception: row.get(6)} },
                    mapper: |it| { <Edge>::from(it) },
                }
            }
        }
pub fn method_edges() -> MethodEdgesStmt {
                MethodEdgesStmt(cornucopia_async::private::Stmt::new("SELECT id, graph_id, source, target, edge_type, direction, exception
FROM edges
WHERE graph_id = $1"))
            }
            pub struct MethodEdgesStmt(cornucopia_async::private::Stmt);
            impl MethodEdgesStmt {pub fn bind<'a, C: GenericClient>(&'a mut self, client: &'a  C, method_id : &'a i32) -> EdgeQuery<'a,C, Edge, 1> {
                EdgeQuery {
                    client,
                    params: [method_id],
                    stmt: &mut self.0,
                    extractor: |row| { EdgeBorrowed {id: row.get(0),graph_id: row.get(1),source: row.get(2),target: row.get(3),edge_type: row.get(4),direction: row.get(5),exception: row.get(6)} },
                    mapper: |it| { <Edge>::from(it) },
                }
            }
        }
pub fn node_incoming_edges() -> NodeIncomingEdgesStmt {
                NodeIncomingEdgesStmt(cornucopia_async::private::Stmt::new("SELECT id, graph_id, source, target, edge_type, direction, exception
FROM edges
WHERE target = $1"))
            }
            pub struct NodeIncomingEdgesStmt(cornucopia_async::private::Stmt);
            impl NodeIncomingEdgesStmt {pub fn bind<'a, C: GenericClient>(&'a mut self, client: &'a  C, target_id : &'a i32) -> EdgeQuery<'a,C, Edge, 1> {
                EdgeQuery {
                    client,
                    params: [target_id],
                    stmt: &mut self.0,
                    extractor: |row| { EdgeBorrowed {id: row.get(0),graph_id: row.get(1),source: row.get(2),target: row.get(3),edge_type: row.get(4),direction: row.get(5),exception: row.get(6)} },
                    mapper: |it| { <Edge>::from(it) },
                }
            }
        }
pub fn node_outgoing_edges() -> NodeOutgoingEdgesStmt {
                NodeOutgoingEdgesStmt(cornucopia_async::private::Stmt::new("SELECT id, graph_id, source, target, edge_type, direction, exception
FROM edges
WHERE source = $1"))
            }
            pub struct NodeOutgoingEdgesStmt(cornucopia_async::private::Stmt);
            impl NodeOutgoingEdgesStmt {pub fn bind<'a, C: GenericClient>(&'a mut self, client: &'a  C, source_id : &'a i32) -> EdgeQuery<'a,C, Edge, 1> {
                EdgeQuery {
                    client,
                    params: [source_id],
                    stmt: &mut self.0,
                    extractor: |row| { EdgeBorrowed {id: row.get(0),graph_id: row.get(1),source: row.get(2),target: row.get(3),edge_type: row.get(4),direction: row.get(5),exception: row.get(6)} },
                    mapper: |it| { <Edge>::from(it) },
                }
            }
        }
pub fn insert_edge() -> InsertEdgeStmt {
                InsertEdgeStmt(cornucopia_async::private::Stmt::new("INSERT INTO edges
    (graph_id, source, target, edge_type, direction, exception)
VALUES
    ($1, $2, $3, $4, $5, $6)
RETURNING *"))
            }
            pub struct InsertEdgeStmt(cornucopia_async::private::Stmt);
            impl InsertEdgeStmt {pub fn bind<'a, C: GenericClient>(&'a mut self, client: &'a  C, graph_id : &'a i32,source : &'a i32,target : &'a i32,edge_type : &'a super::super::types::public::EdgeType,direction : &'a Option<bool>,exception : &'a Option<&'a str>) -> EdgeQuery<'a,C, Edge, 6> {
                EdgeQuery {
                    client,
                    params: [graph_id,source,target,edge_type,direction,exception],
                    stmt: &mut self.0,
                    extractor: |row| { EdgeBorrowed {id: row.get(0),graph_id: row.get(1),source: row.get(2),target: row.get(3),edge_type: row.get(4),direction: row.get(5),exception: row.get(6)} },
                    mapper: |it| { <Edge>::from(it) },
                }
            }
        }impl <'a, C: GenericClient> cornucopia_async::Params<'a, InsertEdgeParams<'a>, EdgeQuery<'a, C, Edge, 6>, C> for InsertEdgeStmt  { 
                    fn params(&'a mut self, client: &'a  C, params: &'a InsertEdgeParams<'a>) -> EdgeQuery<'a, C, Edge, 6> {
                        self.bind(client, &params.graph_id,&params.source,&params.target,&params.edge_type,&params.direction,&params.exception)
                    }
                } }
pub mod graph { use futures::{{StreamExt, TryStreamExt}};use futures; use cornucopia_async::GenericClient; 
#[derive(Debug)]
            pub struct InsertGraphParams<'a> { pub graph_id : &'a str,pub program_id : i32 } #[derive( Debug, Clone, PartialEq,)] pub struct Graph { pub id : i32,pub graph_id : String,pub program_id : i32 }pub struct GraphBorrowed<'a> { pub id : i32,pub graph_id : &'a str,pub program_id : i32 }
                impl<'a> From<GraphBorrowed<'a>> for Graph {
                    fn from(GraphBorrowed { id,graph_id,program_id }: GraphBorrowed<'a>) -> Self {
                        Self { id,graph_id: graph_id.into(),program_id }
                    }
                }
            pub struct GraphQuery<'a, C: GenericClient, T, const N: usize> {
                client: &'a  C,
                params: [&'a (dyn postgres_types::ToSql + Sync); N],
                stmt: &'a mut cornucopia_async::private::Stmt,
                extractor: fn(&tokio_postgres::Row) -> GraphBorrowed,
                mapper: fn(GraphBorrowed) -> T,
            }
            impl<'a, C, T:'a, const N: usize> GraphQuery<'a, C, T, N> where C: GenericClient {
                pub fn map<R>(self, mapper: fn(GraphBorrowed) -> R) -> GraphQuery<'a,C,R,N> {
                    GraphQuery {
                        client: self.client,
                        params: self.params,
                        stmt: self.stmt,
                        extractor: self.extractor,
                        mapper,
                    }
                }
            
                pub async fn one(self) -> Result<T, tokio_postgres::Error> {
                    let stmt = self.stmt.prepare(self.client).await?;
                    let row = self.client.query_one(stmt, &self.params).await?;
                    Ok((self.mapper)((self.extractor)(&row)))
                }
            
                pub async fn all(self) -> Result<Vec<T>, tokio_postgres::Error> {
                    self.iter().await?.try_collect().await
                }
            
                pub async fn opt(self) -> Result<Option<T>, tokio_postgres::Error> {
                    let stmt = self.stmt.prepare(self.client).await?;
                    Ok(self
                        .client
                        .query_opt(stmt, &self.params)
                        .await?
                        .map(|row| (self.mapper)((self.extractor)(&row))))
                }
            
                pub async fn iter(
                    self,
                ) -> Result<impl futures::Stream<Item = Result<T, tokio_postgres::Error>> + 'a, tokio_postgres::Error> {
                    let stmt = self.stmt.prepare(self.client).await?;
                    let it = self
                        .client
                        .query_raw(stmt, cornucopia_async::private::slice_iter(&self.params))
                        .await?
                        
                        .map(move |res| res.map(|row| (self.mapper)((self.extractor)(&row))))
                        .into_stream();
                    Ok(it)
                }
            } pub fn program_graphs() -> ProgramGraphsStmt {
                ProgramGraphsStmt(cornucopia_async::private::Stmt::new("SELECT id, graph_id, program_id
FROM graphs
WHERE program_id = $1"))
            }
            pub struct ProgramGraphsStmt(cornucopia_async::private::Stmt);
            impl ProgramGraphsStmt {pub fn bind<'a, C: GenericClient>(&'a mut self, client: &'a  C, program_id : &'a i32) -> GraphQuery<'a,C, Graph, 1> {
                GraphQuery {
                    client,
                    params: [program_id],
                    stmt: &mut self.0,
                    extractor: |row| { GraphBorrowed {id: row.get(0),graph_id: row.get(1),program_id: row.get(2)} },
                    mapper: |it| { <Graph>::from(it) },
                }
            }
        }
pub fn all_graphs() -> AllGraphsStmt {
                AllGraphsStmt(cornucopia_async::private::Stmt::new("SELECT id, graph_id, program_id
FROM graphs"))
            }
            pub struct AllGraphsStmt(cornucopia_async::private::Stmt);
            impl AllGraphsStmt {pub fn bind<'a, C: GenericClient>(&'a mut self, client: &'a  C, ) -> GraphQuery<'a,C, Graph, 0> {
                GraphQuery {
                    client,
                    params: [],
                    stmt: &mut self.0,
                    extractor: |row| { GraphBorrowed {id: row.get(0),graph_id: row.get(1),program_id: row.get(2)} },
                    mapper: |it| { <Graph>::from(it) },
                }
            }
        }
pub fn insert_graph() -> InsertGraphStmt {
                InsertGraphStmt(cornucopia_async::private::Stmt::new("INSERT INTO graphs (graph_id, program_id) VALUES ($1, $2) RETURNING *"))
            }
            pub struct InsertGraphStmt(cornucopia_async::private::Stmt);
            impl InsertGraphStmt {pub fn bind<'a, C: GenericClient>(&'a mut self, client: &'a  C, graph_id : &'a &'a str,program_id : &'a i32) -> GraphQuery<'a,C, Graph, 2> {
                GraphQuery {
                    client,
                    params: [graph_id,program_id],
                    stmt: &mut self.0,
                    extractor: |row| { GraphBorrowed {id: row.get(0),graph_id: row.get(1),program_id: row.get(2)} },
                    mapper: |it| { <Graph>::from(it) },
                }
            }
        }impl <'a, C: GenericClient> cornucopia_async::Params<'a, InsertGraphParams<'a>, GraphQuery<'a, C, Graph, 2>, C> for InsertGraphStmt  { 
                    fn params(&'a mut self, client: &'a  C, params: &'a InsertGraphParams<'a>) -> GraphQuery<'a, C, Graph, 2> {
                        self.bind(client, &params.graph_id,&params.program_id)
                    }
                } }
pub mod node { use futures::{{StreamExt, TryStreamExt}};use futures; use cornucopia_async::GenericClient; 

#[derive(Debug)]
            pub struct InsertNodeParams<'a> { pub label : Option<&'a str>,pub node_type : super::super::types::public::NodeType,pub contents : Option<&'a str>,pub graph_id : i32 } #[derive( Debug, Clone, PartialEq,)] pub struct Node { pub id : i32,pub label : Option<String>,pub node_type : super::super::types::public::NodeType,pub contents : Option<String>,pub graph_id : i32 }pub struct NodeBorrowed<'a> { pub id : i32,pub label : Option<&'a str>,pub node_type : super::super::types::public::NodeType,pub contents : Option<&'a str>,pub graph_id : i32 }
                impl<'a> From<NodeBorrowed<'a>> for Node {
                    fn from(NodeBorrowed { id,label,node_type,contents,graph_id }: NodeBorrowed<'a>) -> Self {
                        Self { id,label: label.map(|v| v.into()),node_type,contents: contents.map(|v| v.into()),graph_id }
                    }
                }
            pub struct NodeQuery<'a, C: GenericClient, T, const N: usize> {
                client: &'a  C,
                params: [&'a (dyn postgres_types::ToSql + Sync); N],
                stmt: &'a mut cornucopia_async::private::Stmt,
                extractor: fn(&tokio_postgres::Row) -> NodeBorrowed,
                mapper: fn(NodeBorrowed) -> T,
            }
            impl<'a, C, T:'a, const N: usize> NodeQuery<'a, C, T, N> where C: GenericClient {
                pub fn map<R>(self, mapper: fn(NodeBorrowed) -> R) -> NodeQuery<'a,C,R,N> {
                    NodeQuery {
                        client: self.client,
                        params: self.params,
                        stmt: self.stmt,
                        extractor: self.extractor,
                        mapper,
                    }
                }
            
                pub async fn one(self) -> Result<T, tokio_postgres::Error> {
                    let stmt = self.stmt.prepare(self.client).await?;
                    let row = self.client.query_one(stmt, &self.params).await?;
                    Ok((self.mapper)((self.extractor)(&row)))
                }
            
                pub async fn all(self) -> Result<Vec<T>, tokio_postgres::Error> {
                    self.iter().await?.try_collect().await
                }
            
                pub async fn opt(self) -> Result<Option<T>, tokio_postgres::Error> {
                    let stmt = self.stmt.prepare(self.client).await?;
                    Ok(self
                        .client
                        .query_opt(stmt, &self.params)
                        .await?
                        .map(|row| (self.mapper)((self.extractor)(&row))))
                }
            
                pub async fn iter(
                    self,
                ) -> Result<impl futures::Stream<Item = Result<T, tokio_postgres::Error>> + 'a, tokio_postgres::Error> {
                    let stmt = self.stmt.prepare(self.client).await?;
                    let it = self
                        .client
                        .query_raw(stmt, cornucopia_async::private::slice_iter(&self.params))
                        .await?
                        
                        .map(move |res| res.map(|row| (self.mapper)((self.extractor)(&row))))
                        .into_stream();
                    Ok(it)
                }
            } pub fn program_nodes() -> ProgramNodesStmt {
                ProgramNodesStmt(cornucopia_async::private::Stmt::new("SELECT id, label, node_type, contents, graph_id
FROM nodes
WHERE graph_id IN (
    SELECT id
    FROM graphs
    WHERE graphs.program_id = $1
)"))
            }
            pub struct ProgramNodesStmt(cornucopia_async::private::Stmt);
            impl ProgramNodesStmt {pub fn bind<'a, C: GenericClient>(&'a mut self, client: &'a  C, program_id : &'a i32) -> NodeQuery<'a,C, Node, 1> {
                NodeQuery {
                    client,
                    params: [program_id],
                    stmt: &mut self.0,
                    extractor: |row| { NodeBorrowed {id: row.get(0),label: row.get(1),node_type: row.get(2),contents: row.get(3),graph_id: row.get(4)} },
                    mapper: |it| { <Node>::from(it) },
                }
            }
        }
pub fn method_nodes() -> MethodNodesStmt {
                MethodNodesStmt(cornucopia_async::private::Stmt::new("SELECT id, label, node_type, contents, graph_id
FROM nodes
WHERE graph_id = $1"))
            }
            pub struct MethodNodesStmt(cornucopia_async::private::Stmt);
            impl MethodNodesStmt {pub fn bind<'a, C: GenericClient>(&'a mut self, client: &'a  C, graph_id : &'a i32) -> NodeQuery<'a,C, Node, 1> {
                NodeQuery {
                    client,
                    params: [graph_id],
                    stmt: &mut self.0,
                    extractor: |row| { NodeBorrowed {id: row.get(0),label: row.get(1),node_type: row.get(2),contents: row.get(3),graph_id: row.get(4)} },
                    mapper: |it| { <Node>::from(it) },
                }
            }
        }
pub fn insert_node() -> InsertNodeStmt {
                InsertNodeStmt(cornucopia_async::private::Stmt::new("INSERT INTO nodes
    (label, node_type, contents, graph_id)
VALUES ($1, $2, $3, $4)
RETURNING *"))
            }
            pub struct InsertNodeStmt(cornucopia_async::private::Stmt);
            impl InsertNodeStmt {pub fn bind<'a, C: GenericClient>(&'a mut self, client: &'a  C, label : &'a Option<&'a str>,node_type : &'a super::super::types::public::NodeType,contents : &'a Option<&'a str>,graph_id : &'a i32) -> NodeQuery<'a,C, Node, 4> {
                NodeQuery {
                    client,
                    params: [label,node_type,contents,graph_id],
                    stmt: &mut self.0,
                    extractor: |row| { NodeBorrowed {id: row.get(0),label: row.get(1),node_type: row.get(2),contents: row.get(3),graph_id: row.get(4)} },
                    mapper: |it| { <Node>::from(it) },
                }
            }
        }impl <'a, C: GenericClient> cornucopia_async::Params<'a, InsertNodeParams<'a>, NodeQuery<'a, C, Node, 4>, C> for InsertNodeStmt  { 
                    fn params(&'a mut self, client: &'a  C, params: &'a InsertNodeParams<'a>) -> NodeQuery<'a, C, Node, 4> {
                        self.bind(client, &params.label,&params.node_type,&params.contents,&params.graph_id)
                    }
                } }
pub mod program { use futures::{{StreamExt, TryStreamExt}};use futures; use cornucopia_async::GenericClient;  #[derive( Debug, Clone, PartialEq,)] pub struct Program { pub id : i32,pub program_id : String }pub struct ProgramBorrowed<'a> { pub id : i32,pub program_id : &'a str }
                impl<'a> From<ProgramBorrowed<'a>> for Program {
                    fn from(ProgramBorrowed { id,program_id }: ProgramBorrowed<'a>) -> Self {
                        Self { id,program_id: program_id.into() }
                    }
                }
            pub struct ProgramQuery<'a, C: GenericClient, T, const N: usize> {
                client: &'a  C,
                params: [&'a (dyn postgres_types::ToSql + Sync); N],
                stmt: &'a mut cornucopia_async::private::Stmt,
                extractor: fn(&tokio_postgres::Row) -> ProgramBorrowed,
                mapper: fn(ProgramBorrowed) -> T,
            }
            impl<'a, C, T:'a, const N: usize> ProgramQuery<'a, C, T, N> where C: GenericClient {
                pub fn map<R>(self, mapper: fn(ProgramBorrowed) -> R) -> ProgramQuery<'a,C,R,N> {
                    ProgramQuery {
                        client: self.client,
                        params: self.params,
                        stmt: self.stmt,
                        extractor: self.extractor,
                        mapper,
                    }
                }
            
                pub async fn one(self) -> Result<T, tokio_postgres::Error> {
                    let stmt = self.stmt.prepare(self.client).await?;
                    let row = self.client.query_one(stmt, &self.params).await?;
                    Ok((self.mapper)((self.extractor)(&row)))
                }
            
                pub async fn all(self) -> Result<Vec<T>, tokio_postgres::Error> {
                    self.iter().await?.try_collect().await
                }
            
                pub async fn opt(self) -> Result<Option<T>, tokio_postgres::Error> {
                    let stmt = self.stmt.prepare(self.client).await?;
                    Ok(self
                        .client
                        .query_opt(stmt, &self.params)
                        .await?
                        .map(|row| (self.mapper)((self.extractor)(&row))))
                }
            
                pub async fn iter(
                    self,
                ) -> Result<impl futures::Stream<Item = Result<T, tokio_postgres::Error>> + 'a, tokio_postgres::Error> {
                    let stmt = self.stmt.prepare(self.client).await?;
                    let it = self
                        .client
                        .query_raw(stmt, cornucopia_async::private::slice_iter(&self.params))
                        .await?
                        
                        .map(move |res| res.map(|row| (self.mapper)((self.extractor)(&row))))
                        .into_stream();
                    Ok(it)
                }
            } pub fn programs() -> ProgramsStmt {
                ProgramsStmt(cornucopia_async::private::Stmt::new("SELECT id, program_id FROM programs"))
            }
            pub struct ProgramsStmt(cornucopia_async::private::Stmt);
            impl ProgramsStmt {pub fn bind<'a, C: GenericClient>(&'a mut self, client: &'a  C, ) -> ProgramQuery<'a,C, Program, 0> {
                ProgramQuery {
                    client,
                    params: [],
                    stmt: &mut self.0,
                    extractor: |row| { ProgramBorrowed {id: row.get(0),program_id: row.get(1)} },
                    mapper: |it| { <Program>::from(it) },
                }
            }
        }
pub fn insert_program() -> InsertProgramStmt {
                InsertProgramStmt(cornucopia_async::private::Stmt::new("INSERT INTO programs (program_id) VALUES ($1) RETURNING *"))
            }
            pub struct InsertProgramStmt(cornucopia_async::private::Stmt);
            impl InsertProgramStmt {pub fn bind<'a, C: GenericClient>(&'a mut self, client: &'a  C, program_id : &'a &'a str) -> ProgramQuery<'a,C, Program, 1> {
                ProgramQuery {
                    client,
                    params: [program_id],
                    stmt: &mut self.0,
                    extractor: |row| { ProgramBorrowed {id: row.get(0),program_id: row.get(1)} },
                    mapper: |it| { <Program>::from(it) },
                }
            }
        } } }