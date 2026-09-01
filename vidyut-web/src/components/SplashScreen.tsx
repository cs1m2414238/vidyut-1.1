import React, { useEffect, useState } from "react";
import loadingBgImg from "../assets/loadingpage.png";
import "../css/splash.css";

interface SplashScreenProps {
  onComplete: () => void;
}

export const SplashScreen: React.FC<SplashScreenProps> = ({ onComplete }) => {
  const [isFadingOut, setIsFadingOut] = useState(false);

  useEffect(() => {
    // Start fade out at 1.8 seconds (1800ms)
    const fadeTimer = setTimeout(() => {
      setIsFadingOut(true);
    }, 1800);

    // Call completion handler at 2.1 seconds (2100ms)
    const completeTimer = setTimeout(() => {
      onComplete();
    }, 2100);

    return () => {
      clearTimeout(fadeTimer);
      clearTimeout(completeTimer);
    };
  }, [onComplete]);

  return (
    <div className={`splash-screen ${isFadingOut ? "fade-out" : ""}`}>
      {/* EV + Charger full screen background */}
      <div className="splash-bg-wrapper">
        <img
          src={loadingBgImg}
          alt="Vidyut EV Charger Background"
          className="splash-bg-image"
        />
        <div className="splash-overlay" />
      </div>

      {/* Centered cinematic content layout */}
      <div className="splash-content">
        {/* Canonical Vidyut logo */}
        <div className="splash-logo-container">
          <img className="splash-logo-svg" src="/vidyut-logo.svg" alt="" />
        </div>

        {/* VIDYUT Title */}
        <h1 className="splash-title">VIDYUT</h1>

        {/* Tagline */}
        <p className="splash-tagline">Powering a Smarter Tomorrow</p>

        {/* Animated charging line: ⚡ ━━━━━━━━━━━ */}
        <div className="splash-line-wrapper">
          <span className="splash-bolt">⚡</span>
          <div className="splash-line-track">
            <div className="splash-line-fill" />
          </div>
        </div>
      </div>
    </div>
  );
};

export default SplashScreen;
