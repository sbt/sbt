
$ cat ~/.sbt/1.0/server/0845deda85cb41abdb9f/token.json

### initialize

```json
{ "jsonrpc": "2.0", "id": 1, "method": "initialize", "params": { "initializationOptions": { "token": "************" } } }
```

### sbt/exec

```json
{ "jsonrpc": "2.0", "id": 1, "method": "sbt/exec", "params": { "commandLine": "compile" } }
```

### sbt/setting

```json
{ "jsonrpc": "2.0", "id": 1, "method": "sbt/setting", "params": { "setting": "root/name" } }
```

Here's an example of a bad query:

```json
{ "jsonrpc": "2.0", "id": 1, "method": "sbt/setting", "params": { "setting": "name" } }
```


```
Content-Length: 104
Content-Type: application/vscode-jsonrpc; charset=utf-8

{"jsonrpc":"2.0","id":"1","error":{"code":-32602,"message":"Not a valid project ID: name\nname\n    ^"}}
```
