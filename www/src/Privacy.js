import React from 'react';
import './App.css';
import logo from './cyclotrack-app.svg';
import { Flexbox, Text, Link, Icon, Spacer } from 'kvl-react-ui';
import { Link as RouterLink } from 'react-router-dom';

function Privacy() {
  return (
    <Flexbox
      flexDirection="column"
      backgroundColor="#282c34"
      color="#eee"
      padding="1em 1em 0 1em"
      height="100%"
      minHeight="100vh"
    >
      <Flexbox flexDirection="column" maxWidth="80ch" height="100%">
        <RouterLink to="/">
          <img width="20%" src={logo} alt="logo" />
        </RouterLink>
        <Spacer height="3em" />
        <h3>Privacy Policy</h3>
        Cyclotrack uses location data from GPS receiver on your device to
        visualize speed, distance, and map your route. All data collected by
        Cyclotrack is stored on your device. No data is transmitted from your
        device to the developer of Cyclotrack. No data is transmitted to a third
        party unless you explicitly enable a third party integration (e.g.
        Google Fit).
        <Spacer height="2em" />
      </Flexbox>
    </Flexbox>
  );
}

export default Privacy;
