import React from 'react';
import { Home, Search, Library, Download } from 'lucide-react';
import { Link, useLocation } from 'react-router-dom';

const BottomNav = () => {
    const location = useLocation();

    const NavItem = ({ to, icon: Icon, label }) => {
        const isActive = location.pathname === to;
        return (
            <Link
                to={to}
                className={`bottom-nav-item ${isActive ? 'active' : ''}`}
            >
                <Icon size={24} strokeWidth={isActive ? 2.5 : 2} />
                <span>{label}</span>
            </Link>
        );
    };

    return (
        <nav className="bottom-nav">
            <NavItem to="/" icon={Home} label="Home" />
            <NavItem to="/search" icon={Search} label="Search" />
            <NavItem to="/library" icon={Library} label="Library" />
            <NavItem to="/downloads" icon={Download} label="Downloads" />
        </nav>
    );
};

export default BottomNav;
