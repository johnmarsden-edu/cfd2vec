use log::trace;

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
