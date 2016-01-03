# om-next-ideas

An example project illustrating ideas on how to organise an om.next project

This dev workflow emphasizes using clj on the jvm for most of the client and server code.
Note that it still uses figwheel and devcards when doing client dev of the UI layer in the browser.
This also means that lein/component (with reloaded workflow) is the driver of the project - not a script/figwheel.clj file

This may not be your preference if you prefer using cljs tooling.

Definitely opinionated and reserves the right to be totally wrong.
Just looking for feedback or confirmation/critique of the ideas.

## Quick Start

Start by watching Tony Kays video on how om.next works. This helps understand the steps in the integration test

Then read/run the integration test to see client/server data flow

Then run figwheel service to repeat the integration test from a real UI
    - run quickie while working on client or server code
    - run doo autotest TODO
    - make mods to any part of code and see autotests verify correctness and figwheel re-render

Profit!

## Usage

run tests in jvm

    lein test

run test watcher (only sees clj changes)

    lein quickie

run tests in in cljs

    lein doo ; TODO
or

    manually via devcards ; TODO

run full-stack figwheel

    (user/go) from repl

and then browse localhost:8080

run devcards

    ; TODO

## Ideas

- cljc all the things (only the react components and the app init ns are cljs)
    - everything except the view layer can be tested on the jvm
    - easier to run CI tests for client code (lein test vs lein doo)
    - easier to run integration tests of client and server when server is clj e.g. datomic
- separation of concerns: using msgs for mutations from view components
    - msgs translated into parse mutations by a "controller" ns because...
    - views should only know about rendering and sending back user events (called messages)
    - views shouldn't know about tempids
    - views shouldn't know which queries should be re-run after mutations
    - views shouldn't need to decide if a mutation is required i.e. dirty checking
    - controller can be cljc and used in jvm unit/integration tests
    - allows schemas to check messages from UI
    - schemas can generate messages for test.check (experimental) generators
- generative testing using schema generators
    - for unit tests (see core_test.cljc)
    - TODO for integration tests
- parse middleware
    - partially built, waiting to see what the prescribed error handling design looks like
- schema to validate queries (especially on server where client might be hacked)
- :always-validate on mutations
- components (ssierra lib) on server
    - figwheel component started as part of the server
    - figwheel app talks the the real api during development
- dirty tracking of local state data to control when remote mutation is required

### Important cljc fns

To enable a simulated client on the jvm, a few key fns provided by om.next in cljs needed to be
ported to cljc. Each of these is a guess at the correct behaviour but is likely to not be totally
correct for all use-cases.

If you spot a problem, please log an issue so a test/fix can be created. Or even better, submit a pull-request
with a test and fix.

They are:

- normalized->tree and tree->normalized
- resolve-tempids
- portable-merge

### TODO / Future

- build an om.next main.js to compare portable code vs cljs migrate etc
- reloaded dev ns
- devcards working
- cljs repl (using piggieback) working
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

## Design

- source layout (src and test)
    - cljc : anything that can be tested in the jvm
    - cljs : code that only runs in a browser e.g. view layer, app start, devcards
    - clj  : server/remote code

- interesting tests
    - parsing-tests-local : stateful tests of a client without a server/remote
    - parsing-integration-tests : stateful tests of a client and a server/remote together
    - core-test : unit and test.check for ported om.next fns

### Gotchas

- lein quickie does not see changes to cljc files. fixed yet?

- using logging with tempids is tricky because pprint and pr-str display tempids differently.
  pprint vs println display differently when using the same value.


## Roadmap / Need help

- get lein doo working
- get lein quickie to see changes to cljc files

## Questions that need answers

- how to re-render only the matching display component when a edit component mutates a change?
- how to stop ident removal from [:ui :dirty] from re-rendering all people. ideally none should re-render.
    - it could be from the remote response update, not the removal of dirty flags
- how to stop Cursive from opening source files from resource dir
- will norm/denorm fns work for all query types/results?

