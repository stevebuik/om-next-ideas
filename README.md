# om-next-ideas

An example project illustrating ideas on how to organise an om.next project

This dev workflow emphasizes using clj on the jvm for most of the client (and server) code.
This may not be your preference if you like using cljs tooling but it still uses
figwheel and devcards when doing client dev.

Definitely opinionated and reserves the right to be totally wrong. Just looking for feedback or confirmation of the ideas.

## Quick Start

Start by watching Tony Kays video on how om.next works. This helps understand the steps in the integration test

Then read/run the integration test to see client/server data flow

Then run figwheel service to repeat the integration test from a real UI
    - run quickie while working on client or server code
    - run doo ?
    - make mods to any part of code and see autotests verify correctness and figwheel re-render

Profit!

## Usage

run tests in jvm

    lein do clean, test

run test watcher (only sees clj changes)

    lein quickie

run tests in in cljs

    lein doo
or

    manually via devcards

run full-stack figwheel

    (user/go) from repl

run devcards

## Ideas

- heavily jvm/clj focused
- cljc all the things, even client parser
    - easier to run CI tests for client code
    - easier to run integration tests of client and server when server is clj
- separation of concerns: using msgs for mutations from view components
    - msgs translated into parse mutations by a controller ns because...
    - views shouldn't know about tempids
    - views shouldn't know which queries should be re-run after mutations
    - controller can be cljc and used in jvm unit/integration tests
- controller is cljc to support jvm tests using messages i.e. test everything except render fn
    - allows schemas to check messages
    - can re-run post-mutation queries and test on jvm
    - schemas can generate messages for test.check
- generative testing using schema generators
    - for unit tests
    - TODO for integration tests
- parse middleware
- schema to validate queries (especially on server where client might be hacked)
- :always-validate on mutations
- components (ssierra lib) on server
    - figwheel component started as part of the server
    - figwheel app talks the the real api during development

### TODO / Future

- composability: re-usable widgets use ::ns/foo to keep state in single atom but hygienically. N of same widgets used concurrently?
- abstract components
- check cljc parser matches output of om/db->tree in devcards tests
    - maybe even generative
- remote query generation
    - use absence of key in tables to include a query node (and sub-queries) in the remote query
- defuip macro to make ui components portable
    - usable in integration tests
    - extract queries to allow construction of root query
        - use this for host page state seed?
        - server side rendering of initial app state?
    - extract mutation messages to verify against controller schema
- is it possible to parse and generate docs for a parser?
    - if using a query schema then yes for the query
- event bus?

## Design

- source layout (src and test)
    - cljc : anything that can be tested in the jvm
    - cljs : code that only runs in a browser e.g. view layer, app start, devcards
    - clj  : server/remote code
- messages from UI layer include OmIdents but read/write parse calls use Id

### Observations

- can't use db->tree and tree->db since they are (cljs only)
- can't use migrate to process tempids returned by remote (cljs only)

### Gotchas

- lein quickie does not see changes to cljc files. fixed yet?

- using logging on tempids is tricky because pprint and pr-str display tempids differently.
  pprint vs println display differently when using the same value.


## Roadmap / Need help

- get lein doo working

## Questions that need answers

- how to stop Cursive from opening source files from resource dir
- how should controller have access to state? there are cases for stateful msg transformation
- will norm/denorm fns work for all query results?

