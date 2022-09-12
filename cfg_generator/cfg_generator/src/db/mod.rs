use crate::server::error::MessageProcessingError;
use futures::pin_mut;
use log::{info, trace};
use postgres_types::{ToSql, Type};
use tokio_postgres::binary_copy::BinaryCopyInWriter;
use tokio_postgres::Transaction;

pub mod cornucopia;

pub struct Config {
    pub(crate) pg: tokio_postgres::Config,
}

impl Config {
    pub fn from_env() -> Result<Self, config::ConfigError> {
        let config = config::Config::builder()
            .add_source(
                config::Environment::with_prefix("CFG_GEN")
                    .try_parsing(true)
                    .separator("__"),
            )
            .set_default("db.name", "cfg_gen")?
            .build()?;

        trace!("{:#?}", config);
        let mut pg_config = tokio_postgres::config::Config::new();
        pg_config.host(config.get_string("db.host")?.as_str());
        pg_config.dbname(config.get_string("db.name")?.as_str());
        pg_config.user(config.get_string("db.user")?.as_str());
        pg_config.password(config.get_string("db.password")?.as_str());
        Ok(Config { pg: pg_config })
    }
}

pub async fn store_values<'a>(
    transaction: &Transaction<'a>,
    table: String,
    columns: String,
    types: &'a [Type],
    values: &'a [&'a [&'a (dyn ToSql + Sync)]],
) -> Result<Vec<i32>, MessageProcessingError> {
    trace!("Start copy sink");
    let copy_sink = transaction
        .copy_in(&format!(
            "COPY {}_temp ({}) FROM STDIN (FORMAT binary)",
            table, columns
        ))
        .await?;

    trace!("Write data to binary copy writer");
    let writer = BinaryCopyInWriter::new(copy_sink, types);
    pin_mut!(writer);
    for value in values {
        writer.as_mut().write(value).await?;
    }
    writer.finish().await?;

    trace!("Insert IDs from temp table to permanent table");
    let ids: Vec<i32> = transaction
        .query(
            &format!(
                "INSERT INTO {} OVERRIDING USER VALUE SELECT * FROM {}_temp RETURNING id",
                table, table
            ),
            &[],
        )
        .await?
        .into_iter()
        .map(|row| row.get(0))
        .collect();

    info!(
        "Inserted {} IDs into table {} with columns ({}) from {}_temp",
        ids.len(),
        table,
        columns,
        table
    );
    Ok(ids)
}
