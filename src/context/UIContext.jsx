
import React, { createContext, useContext, useState } from 'react';

const UIContext = createContext();

export const useUI = () => useContext(UIContext);

export const UIProvider = ({ children }) => {
    const [isProfileMenuOpen, setIsProfileMenuOpen] = useState(false);

    const openProfileMenu = () => setIsProfileMenuOpen(true);
    const closeProfileMenu = () => setIsProfileMenuOpen(false);
    const toggleProfileMenu = () => setIsProfileMenuOpen(prev => !prev);

    return (
        <UIContext.Provider value={{ isProfileMenuOpen, openProfileMenu, closeProfileMenu, toggleProfileMenu }}>
            {children}
        </UIContext.Provider>
    );
};
