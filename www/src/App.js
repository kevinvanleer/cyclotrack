import React from 'react';
import './App.css';
import logo from './cyclotrack-app.svg';
import { Flexbox, Text, Link, Icon, Spacer } from 'kvl-react-ui';

function App() {
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
        <Link
          className="App-link"
          href="https://reactjs.org"
          target="_blank"
          rel="noopener noreferrer"
        >
          <Flexbox flexDirection="column" alignItems="center">
            <img width="50%" src={logo} alt="logo" />
            <Text fontSize="4em">Cyclotrack</Text>
            <Spacer height="2em" />
            <Text>Download Cyclotrack from the Google Play Store</Text>
          </Flexbox>
        </Link>
        <Spacer height="3em" />
        <h3>Privacy Policy</h3>
        All data collected by Cyclotrack is stored on your device. No data is
        transmitted from your device to the developer of Cyclotrack or any third
        party. If you choose to integrate Cyclotrack with a third party app, it
        is your responsibility to protect your data.
        <Spacer height="2em" />
        <h3>Become a beta tester</h3>
        <Link
          className="App-link"
          href="https://play.google.com/apps/testing/com.kvl.cyclotrack"
        >
          Join beta testing from the web
        </Link>
        <Link
          className="App-link"
          href="https://play.google.com/store/apps/details?id=com.kvl.cyclotrack"
        >
          Join beta testing from Google Play on Android
        </Link>
      </Flexbox>
    </Flexbox>
  );
}

export default App;
