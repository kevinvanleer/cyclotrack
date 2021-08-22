(this.webpackJsonpcyclotrack=this.webpackJsonpcyclotrack||[]).push([[0],{23:function(e,t,a){},34:function(e,t,a){},58:function(e,t,a){"use strict";a.r(t);var i=a(0),o=a.n(i),c=a(25),r=a.n(c),s=(a(34),a(13)),n=a(5),l=(a(23),a.p+"static/media/cyclotrack-app.822f0f32.svg"),d=a.p+"static/media/screenshot-summary.9e1b427c.jpg",h=a.p+"static/media/screenshot-dash-ble.ecf682ce.jpg",g=a.p+"static/media/screenshot-details-1.1ed19eaf.jpg",m=a.p+"static/media/screenshot-details-2.61c12eec.jpg",p=a.p+"static/media/screenshot-details-3.41415b1c.jpg",j=a.p+"static/media/screenshot-details-4.d16d5f2b.jpg",b=a(3),y=a(1);var u=function(){return Object(y.jsx)(b.a,{flexDirection:"column",backgroundColor:"#282c34",color:"#eee",padding:"1em 1em 0 1em",height:"100%",minHeight:"100vh",children:Object(y.jsxs)(b.a,{flexDirection:"column",maxWidth:"80ch",height:"100%",children:[Object(y.jsx)(b.b,{className:"App-link",href:"https://play.google.com/store/apps/details?id=com.kvl.cyclotrack",target:"_blank",rel:"noopener noreferrer",color:"#669e58",children:Object(y.jsxs)(b.a,{flexDirection:"column",alignItems:"center",children:[Object(y.jsx)("img",{width:"50%",src:l,alt:"logo"}),Object(y.jsx)(b.d,{fontSize:"4em",children:"Cyclotrack"}),Object(y.jsx)(b.c,{height:"2em"}),Object(y.jsx)(b.d,{children:"Download Cyclotrack from the Google Play Store"})]})}),Object(y.jsx)(b.c,{height:"3em"}),Object(y.jsxs)(b.a,{flexDirection:"row",justifyContent:"center",marginBetween:"30px",children:[Object(y.jsx)("img",{width:"25%",src:d,alt:"summary view screenshot"}),Object(y.jsx)("img",{width:"25%",src:h,alt:"dashboard screenshot"}),Object(y.jsx)("img",{width:"25%",src:g,alt:"details screenshot"})]}),Object(y.jsx)(b.c,{height:"3em"}),"I started Cyclotrack because I could not find a cycle computer app that provided an always-on dashboard with relevant performance data. I also wanted to build an app that was functional using only onboard sensors, primarily GPS. And finally, I wanted a record of my past rides so I could track progress toward my goals and compare with past performance.",Object(y.jsx)(b.c,{height:"1em"}),"As I developed Cyclotrack, the shortcomings of smartphone GPS became apparent. The relatively low speed of cycling, shaded bike paths, buildings, and even cloudy days seemed to make tracking speed frustrating and unreliable. As a result I added support for Bluetooth Low Engery (BLE) heart rate, speed, and cadence sensors. This added a new level of fidelity to the data available to Cyclotrack, providing a much more reliable source for speed data and adding the ability to track pedaling cadence. Tracking heart rate provided a missing piece of information crucial to understanding how much effort I was expending on my rides. Over time, I was able to identify my optimal heart rate for pushing myself without burning out.",Object(y.jsx)(b.c,{height:"1em"}),"I wanted to have rich data analysis without relying on cloud services or third-party integrations with platforms like Strava or Google Fit, so Cyclotrack provides detailed ride analysis with, maps, graphs, and stats for each ride. This data is stored efficiently on your device, and can be exported to XLSX format so you can do your own analysis in your favorite spreadsheet application. Cyclotrack is fully functional offline, no network connection is required to utilize any of its features. Your data is safe. I do not collect any data or analytics (although I may collect anonymous app usage data in the future) and I do not send or sell any data to third parties.",Object(y.jsx)(b.c,{height:"1em"}),"Having said all that, I wanted to track all my fitness activity in a single experience, so I built an integration between Cyclotrack and Google Fit. This integration enables Cyclotrack to send data to Google Fit so that my rides contribute to my heart points, calories, miles, and active minutes. Cyclotrack rides also appear in the Google Fit journal. There was another reason I wanted this integration. Until this point I had been entering my weight in Google Fit for a historical record, and Cyclotrack so that my weight could be used to help estimate calorie burn on my rides. I updated Cyclotrack to read all relevant (and available) biometric data from Google Fit. Cyclotrack reads height, weight, and resting heart rate data from Google Fit. It uses this information, along with BLE heart rate data (if available), to estimate calorie burn. The best part is that this feature is totally optional. Cyclotrack is 100% functional even if you do not use Google Fit.",Object(y.jsx)(b.c,{height:"3em"}),Object(y.jsxs)(b.a,{flexDirection:"row",justifyContent:"center",marginBetween:"10px",children:[Object(y.jsx)("img",{width:"23%",src:g,alt:"details screenshot with map"}),Object(y.jsx)("img",{width:"23%",src:m,alt:"details screenshot with graphs"}),Object(y.jsx)("img",{width:"23%",src:p,alt:"details screenshot with splits"}),Object(y.jsx)("img",{width:"23%",src:j,alt:"details screenshot with elevation chart"})]}),Object(y.jsx)(b.c,{height:"3em"}),Object(y.jsx)("h3",{children:"Become a beta tester"}),Object(y.jsx)(b.b,{color:"#669e58",className:"App-link",href:"https://play.google.com/apps/testing/com.kvl.cyclotrack",children:"Join beta testing from the web"}),Object(y.jsx)(b.b,{color:"#669e58",className:"App-link",href:"https://play.google.com/store/apps/details?id=com.kvl.cyclotrack",children:"Join beta testing from Google Play on Android"}),Object(y.jsx)(b.c,{height:"2em"}),Object(y.jsx)(s.b,{color:"#669e58",className:"App-link",to:"/privacy",href:"/privacy",children:"View Privacy policy"}),Object(y.jsx)(b.c,{height:"2em"})]})})};var f=function(){return Object(y.jsx)(b.a,{flexDirection:"column",backgroundColor:"#282c34",color:"#eee",padding:"1em 1em 0 1em",height:"100%",minHeight:"100vh",children:Object(y.jsxs)(b.a,{flexDirection:"column",maxWidth:"80ch",height:"100%",children:[Object(y.jsx)(s.b,{to:"/",children:Object(y.jsx)("img",{width:"20%",src:l,alt:"logo"})}),Object(y.jsx)(b.c,{height:"3em"}),Object(y.jsx)("h3",{children:"Privacy Policy"}),"Cyclotrack uses location data from GPS receiver on a user device to visualize speed, distance, and map a route. All data collected by Cyclotrack is stored on the device. None of your location or fitness data is transmitted from the device to the developer of Cyclotrack. No data is transmitted to a third party unless you explicitly enable a third party integration (e.g. Google Fit).",Object(y.jsx)("h4",{children:"Google Fit"}),"A user may choose to link Cyclotrack with a Google Fit account. This is done by signing in to a Google account in Cyclotrack settings. When linked Cyclotrack will:",Object(y.jsxs)("ul",{children:[Object(y.jsx)("li",{children:"Read height, weight, and resting heart rate stored in Google Fit. This data will be used to assist in calculating metrics like calorie burn. This data will be stored in Cyclotrack as part of a ride. The data will persist until the ride is deleted from Cyclotrack."}),Object(y.jsx)("li",{children:"Send data to Google Fit to be entered in the activity journal and used to track toward Google Fit goals. These include: location, distance, speed, cadence, and heart rate."})]}),"No data received from Google Fit will be shared with or transmitted to any third party.",Object(y.jsx)(b.c,{height:"1em"}),Object(y.jsx)("span",{children:Object(y.jsxs)("i",{children:[Object(y.jsx)("b",{children:"Disclosure:"})," Use and transfer to any other app of information received from Google APIs by Cyclotrack will adhere to the"," ",Object(y.jsx)(b.b,{href:"https://developers.google.com/terms/api-services-user-data-policy",children:"Google API Services User Data Policy"}),", including the Limited Use requirements."]})}),Object(y.jsx)("h4",{children:"Google Analytics"}),'Cyclotrack uses Google Analytics to help understand user behavior and improve the overall user experience. The Advertising ID and the Secure Settings Android ID (SSAID) are not collected. You may opt out of analytics collection at any time by disabling "Data collection" in app settings.',Object(y.jsx)(b.c,{height:"2em"})]})})};function x(){return Object(y.jsx)(s.a,{children:Object(y.jsxs)(n.c,{children:[Object(y.jsx)(n.a,{path:"/privacy",children:Object(y.jsx)(f,{})}),Object(y.jsx)(n.a,{path:"/",children:Object(y.jsx)(u,{})})]})})}var w=function(e){e&&e instanceof Function&&a.e(3).then(a.bind(null,59)).then((function(t){var a=t.getCLS,i=t.getFID,o=t.getFCP,c=t.getLCP,r=t.getTTFB;a(e),i(e),o(e),c(e),r(e)}))};r.a.render(Object(y.jsx)(o.a.StrictMode,{children:Object(y.jsx)(x,{})}),document.getElementById("root")),w()}},[[58,1,2]]]);
//# sourceMappingURL=main.57b0098f.chunk.js.map