import React from 'react';
import './App.css';
import logo from './cyclotrack-app.svg';

function App() {
  return (
    <div className="App">
      <header className="App-header">
        <img src={logo} alt="logo" />
        <h1>Cyclotrack</h1>
        <a
          className="App-link"
          href="https://reactjs.org"
          target="_blank"
          rel="noopener noreferrer"
        >
          Download Cyclotrack from the Google Play Store
        </a>
        <h3>Privacy Policy</h3>
        All data collected by Cyclotrack is stored on your device. No data is
        transmitted from your device to the developer of Cyclotrack or any third
        party. If you choose to integrate Cyclotrack with a third party app, it
        is your responsibility to protect your data.
      </header>
    </div>
  );
}

export default App;
