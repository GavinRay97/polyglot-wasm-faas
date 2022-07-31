use std::io::Read;
use serde_json::Value;
use std::error::Error;

fn stdin_to_json() -> Result<Value, Box<dyn Error>> {
    let mut buffer = String::new();
    std::io::stdin().read_to_string(&mut buffer)?;
    Ok(serde_json::from_str(&buffer)?)
}

fn write_json_to_stdout(json: &Value) {
    let json_str = serde_json::to_string(json).unwrap();
    println!("{}", json_str);
}

#[no_mangle]
pub fn handler() {
    // Read serde JSON from stdin
    let input = stdin_to_json().unwrap();
    let name = input["name"].as_str().unwrap();
    let example_json = serde_json::json!({
        "name_twice":  format!("{} {}", name, name),
    });
    write_json_to_stdout(&example_json);
}
