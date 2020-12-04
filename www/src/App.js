import React from 'react';
import { BrowserRouter as Router, Switch, Route } from 'react-router-dom';
import Home from './Home.js';
import Privacy from './Privacy.js';

export default function App() {
  return (
    <Router basename="/cyclotrack">
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
