# om-next-ideas

An example project illustrating some ideas on how to organise an om.next project

Definitely opinionated and reserves the right to be totally wrong. Just looking for feedback or confirmation of the ideas.

## Usage

run tests in jvm

    lein do clean, test

run tests in in cljs

compile normal, advanced

run figwheel

run devcards

## Ideas

- separation of concerns: using msgs (schema not defrecord = more flex e.g. case or multimethod better errors) from view components
- controller is cljc to support jvm tests using messages i.e. test everything except render fn
- cljc all the things
- composability: re-usable widgets use ::ns/foo to keep state in single atom but hygienically. N of same widgets used concurrently?
- testing patterns
- is it possible to parse and generate docs for a parser?
- event bus
- parse middleware

### Roadmap / Need help

- using component to configure/init

### Questions that need answers

- how should controller have access to state?

## License

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
