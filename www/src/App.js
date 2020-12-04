import React from 'react';
import { HashRouter as Router, Switch, Route } from 'react-router-dom';
import Home from './Home.js';
import Privacy from './Privacy.js';

export default function App() {
  return (
    <Router>
      <Switch>
        <Route path="/privacy">
          <Privacy />
        </Route>
        <Route path="/">
          <Home />
        </Route>
      </Switch>
    </Router>
  );
}
